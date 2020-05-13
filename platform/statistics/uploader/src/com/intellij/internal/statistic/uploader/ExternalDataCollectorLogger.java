// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader;

import com.intellij.internal.statistic.eventLog.DataCollectorDebugLogger;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class ExternalDataCollectorLogger implements DataCollectorDebugLogger {
  @NonNls private final Logger myLogger;

  public ExternalDataCollectorLogger() {
    myLogger = Logger.getLogger("com.intellij.internal.statistic.uploader");

    String logDirectory = findDirectory(1_000_000L);
    if (logDirectory != null) {
      String directory = new File(logDirectory, "idea_statistics_uploader.log").getAbsolutePath();
      myLogger.addAppender(newAppender(directory));
      myLogger.setLevel(Level.ALL);
    }
  }

  @Nullable
  public static String findDirectory(long requiredFreeSpace) {
    String dir = System.getProperty("java.io.tmpdir");
    if (dir != null && isValidDir(dir, requiredFreeSpace)) {
      return dir;
    }
    return null;
  }

  private static boolean isValidDir(String path, long space) {
    File dir = new File(path);
    return dir.isDirectory() && dir.canWrite() && dir.getUsableSpace() >= space;
  }

  @NotNull
  private static FileAppender newAppender(@NotNull String directory) {
    @NonNls FileAppender appender = new FileAppender();
    appender.setFile(directory);
    appender.setLayout(new PatternLayout("%d{dd/MM HH:mm:ss} %-5p %C - %m%n"));
    appender.setThreshold(Level.ALL);
    appender.setAppend(false);
    appender.activateOptions();
    return appender;
  }

  @Override
  public void info(String message) {
    myLogger.info(message);
  }

  @Override
  public void info(String message, Throwable t) {
    myLogger.info(message, t);
  }

  @Override
  public void warn(String message) {
    myLogger.warn(message);
  }

  @Override
  public void warn(String message, Throwable t) {
    myLogger.warn(message, t);
  }

  @Override
  public void trace(String message) {
    myLogger.trace(message);
  }

  @Override
  public boolean isTraceEnabled() {
    return myLogger.isTraceEnabled();
  }
}
