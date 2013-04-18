/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.hiveio.benchmark;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.api.ThriftHiveMetastore;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.log4j.Logger;

import com.facebook.hiveio.common.HadoopNative;
import com.facebook.hiveio.common.HiveMetastores;
import com.facebook.hiveio.input.HiveApiInputFormat;
import com.facebook.hiveio.input.HiveInputDescription;
import com.facebook.hiveio.record.HiveReadableRecord;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.TimerContext;
import com.yammer.metrics.reporting.ConsoleReporter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for input reading
 */
class InputBenchmark {
  /** Logger */
  private static final Logger LOG = Logger.getLogger(InputBenchmark.class);

  public InputBenchmark() { }

  public void run(InputBenchmarkCmd args) throws Exception {
    HadoopNative.requireHadoopNative();

    Timer allTime = Metrics.newTimer(InputBenchmark.class, "all-time", TimeUnit.MILLISECONDS, TimeUnit.MILLISECONDS);
    TimerContext allTimerContext = allTime.time();

    HiveInputDescription input = new HiveInputDescription();
    input.setDbName(args.database);
    input.setTableName(args.table);
    input.setPartitionFilter(args.partitionFilter);

    HiveConf hiveConf = new HiveConf(InputBenchmark.class);
    ThriftHiveMetastore.Iface client = HiveMetastores.create(args.hiveHost, args.hivePort);

    System.err.println("Initialize profile with input data");
    HiveApiInputFormat.setProfileInputDesc(hiveConf, input, HiveApiInputFormat.DEFAULT_PROFILE_ID);

    HiveApiInputFormat defaultInputFormat = new HiveApiInputFormat();
    if (args.trackMetrics) {
      defaultInputFormat.setObserver(new MetricsObserver("default", args.recordPrintPeriod));
    }

    List<InputSplit> splits = defaultInputFormat.getSplits(hiveConf, client);
    System.err.println("getSplits returned " + splits.size() + " splits");

    long numRows = 0;
    for (int i = 0; i < splits.size(); ++i) {
      InputSplit split = splits.get(i);
      TaskAttemptID taskID = new TaskAttemptID();
      TaskAttemptContext taskContext = new TaskAttemptContext(hiveConf, taskID);
      if (i % args.splitPrintPeriod == 0) {
        System.err.println("Handling split " + i + " of " + splits.size());
      }
      RecordReader<WritableComparable, HiveReadableRecord> reader =
          defaultInputFormat.createRecordReader(split, taskContext);
      reader.initialize(split, taskContext);
      numRows += readFully(reader);
    }

    System.err.println("Parsed " + numRows + " rows");

    allTimerContext.stop();

    new ConsoleReporter(System.err).run();
  }

  /**
   * Read all records from a RecordReader
   *
   * @param reader RecordReader
   * @throws IOException I/O errors
   * @throws InterruptedException thread errors
   */
  private static long readFully(RecordReader<WritableComparable, HiveReadableRecord> reader)
    throws IOException, InterruptedException
  {
    long num = 0;
    while (reader.nextKeyValue()) {
      HiveReadableRecord record = reader.getCurrentValue();
      parseLongLongDouble(record);
      ++num;
//      if (num % 1000000 == 0) {
//        System.out.println("Parsed " + num + " rows");
//      }
    }
    return num;
  }

  private static int rowsPrinted = 0;

  private static void parseLongLongDouble(HiveReadableRecord record) {
    record.getLong(0);
    record.getLong(1);
    record.getDouble(2);
  }
}
