// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.concurrency.SynchronizedClearableLazy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public final class MemTester {
  private MemTester() { }

  private static final Supplier<Boolean> ourMemTesterSupported = new SynchronizedClearableLazy<>(() -> {
    String problem;

    if (SystemInfo.isWindows) {
      problem = "Windows is not supported";
    }
    else if (SystemInfo.isMac) {
      problem = checkMemTester("memtester");
    }
    else if (SystemInfo.isUnix) {
      problem = checkMemTester("memtester");
    }
    else {
      problem = "Platform unsupported: " + SystemInfo.OS_NAME;
    }

    if (problem == null) {
      return true;
    }
    else {
      Logger.getInstance(MemTester.class).info("not supported: " + problem);
      return false;
    }
  });

  private static Boolean isRunning = false;

  /**
   * Checks if userspace memtester is supported on this platform and can be launched.
   *
   * @return  true if memtester can be launched, otherwise false
   */
  public static boolean isSupported() {
    return ourMemTesterSupported.get();
  }

  private static String checkMemTester(String memtesterName) {
    Path memtester = PathManager.findBinFile(memtesterName);
    return memtester != null && Files.isExecutable(memtester) ? null : "not an executable file: " + memtester;
  }

  /**
   * Launches userspace memtester app as a separate native process, the output is redirected
   * into file.
   *
   * @param  memSize        String with memory size to allocate and test, in gigabytes,
   *                        with G suffix (e.g. 1G, 16G)
   * @param  iterations     String with number of memtest iterations to perform
   * @param  outputFilePath String with path to file where the output of the tool is redirected
   * @exception IOException If memtester is not supported on this platform
   */
  public static void scheduleMemTester(String memSize, String iterations, String outputFilePath) throws IOException {
    if (isRunning) {
      Logger.getInstance(MemTester.class).info("Memtester is running already, can't run twice");
      throw new IOException("Cannot start memtester application: already running.");
    }
    if (SystemInfo.isMac || SystemInfo.isUnix) {
      startMemTesterOnMacAndUnix(memSize, iterations, outputFilePath);
    } else {
      Logger.getInstance(MemTester.class).info("not supported on this system");
      throw new IOException("Cannot start memtester application: not supported.");
    }
  }

  private static void startMemTesterOnMacAndUnix(String memSize, String iterations, String outputFilePath) throws IOException {
    List<String> args = new ArrayList<>();
    args.add(memSize);
    args.add(iterations);
    Collections.addAll(args);

    runMemTester(new File(PathManager.getBinPath(), "memtester"), args, new File(outputFilePath));
  }

  private static void runMemTester(File memtesterFile, List<String> memtesterArgs, File outputFile) throws IOException {
    String memtester = memtesterFile.getPath();

    memtesterArgs.add(0, memtester);
    Logger.getInstance(MemTester.class).info("run memtester: " + memtesterArgs);
    ProcessBuilder processBuilder = new ProcessBuilder(memtesterArgs);
    processBuilder.redirectOutput(outputFile);
    processBuilder.start();
    isRunning = true;
  }

}
