// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.util.io.FileUtil;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * @author peter
 */
final class IoTimings {
  private static final int WARM_UP_PROBES = 2;
  private static final int IO_PROBES = 42;

  static long calcIoTiming() {
    long start = System.nanoTime();
    for (int i = 0; i < WARM_UP_PROBES; i++) {
      singleIteration(i, true);
    }
    long warmupMinutes = TimeUnit.NANOSECONDS.toMinutes(System.nanoTime() - start);
    if (warmupMinutes > 1) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("Too long warmup: " + warmupMinutes + " minutes");
    }

    start = System.nanoTime();
    for (int i = 0; i < IO_PROBES; i++) {
      singleIteration(i, i == IO_PROBES - 1);
    }
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  @SuppressWarnings("ImplicitDefaultCharsetUsage")
  private static void singleIteration(int i, boolean doSync) {
    try {
      final File tempFile = FileUtil.createTempFile("test", "test" + i);

      try (FileWriter writer = new FileWriter(tempFile)) {
        for (int j = 0; j < 15; j++) {
          writer.write("test" + j);
          writer.flush();
        }
      }

      try (FileReader reader = new FileReader(tempFile)) {
        //noinspection StatementWithEmptyBody
        while (reader.read() >= 0) {
        }
      }

      if (doSync) {
        try (FileOutputStream stream = new FileOutputStream(tempFile)) {
          stream.getFD().sync();
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
}
