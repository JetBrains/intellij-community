/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework;

import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;

import java.io.*;
import java.math.BigInteger;

/**
 * @author peter
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class Timings {
  private static final int IO_PROBES = 42;

  public static final long CPU_TIMING;
  public static final long IO_TIMING;
  public static final long MACHINE_TIMING;

  /**
   * Measured on dual core p4 3HZ 1gig ram
   */
  public static final long ETALON_TIMING = 438;
  public static final long ETALON_CPU_TIMING = 200;
  public static final long ETALON_IO_TIMING = 100;


  static {
    int N = 20;
    for (int i=0; i<N; i++) {
      measureCPU(); //warmup
    }
    long[] elapsed=new long[N];
    for (int i=0; i< N; i++) {
      elapsed[i] = measureCPU();
    }
    CPU_TIMING = ArrayUtil.averageAmongMedians(elapsed, 2);

    long start = System.currentTimeMillis();
    for (int i = 0; i < IO_PROBES; i++) {
      try {
        final File tempFile = FileUtil.createTempFile("test", "test" + i);

        final FileWriter writer = new FileWriter(tempFile);
        try {
          for (int j = 0; j < 15; j++) {
            writer.write("test" + j);
            writer.flush();
          }
        }
        finally {
          writer.close();
        }

        final FileReader reader = new FileReader(tempFile);
        try {
          while (reader.read() >= 0) {}
        }
        finally {
          reader.close();
        }

        if (i == IO_PROBES - 1) {
          final FileOutputStream stream = new FileOutputStream(tempFile);
          try {
            stream.getFD().sync();
          }
          finally {
            stream.close();
          }
        }

        if (!tempFile.delete()) {
          throw new IOException("Unable to delete: " + tempFile);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    IO_TIMING = System.currentTimeMillis() - start;

    MACHINE_TIMING = CPU_TIMING + IO_TIMING;
  }

  private static long measureCPU() {
    long start = System.currentTimeMillis();

    BigInteger k = new BigInteger("1");
    for (int i = 0; i < 1000000; i++) {
      k = k.add(new BigInteger("1"));
    }

    return System.currentTimeMillis() - start;
  }

  /**
   * @param value the value (e.g. number of iterations) which needs to be adjusted according to my machine speed
   * @param isParallelizable true if the test load is scalable with the CPU cores
   * @return value calibrated according to this machine speed. For slower machine, lesser value will be returned
   */
  public static int adjustAccordingToMySpeed(int value, boolean isParallelizable) {
    return Math.max(1, (int)(1.0 * value * ETALON_TIMING / MACHINE_TIMING) / 8 * (isParallelizable ? JobSchedulerImpl.CORES_COUNT : 1));
  }

  public static String getStatistics() {
    return
      " Timings: CPU=" + CPU_TIMING + " (" + (int)(CPU_TIMING*1.0/ ETALON_CPU_TIMING*100) + "% of the etalon)" +
      ", I/O=" + IO_TIMING + " (" + (int)(IO_TIMING*1.0/ ETALON_IO_TIMING*100) + "% of the etalon)" +
      ", total=" + MACHINE_TIMING + " ("+(int)(MACHINE_TIMING*1.0/ ETALON_TIMING*100) + "% of the etalon) " +
      Runtime.getRuntime().availableProcessors() + " cores.";
  }
}
