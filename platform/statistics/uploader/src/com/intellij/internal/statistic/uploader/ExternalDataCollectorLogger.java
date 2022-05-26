// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader;

import com.intellij.internal.statistic.eventLog.DataCollectorDebugLogger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.*;

public class ExternalDataCollectorLogger implements DataCollectorDebugLogger {
  @SuppressWarnings("NonConstantLogger") @NonNls private final Logger myLogger;

  public ExternalDataCollectorLogger() {
    myLogger = Logger.getLogger("com.intellij.internal.statistic.uploader");

    String logDirectory = findDirectory(1_000_000L);
    if (logDirectory != null) {
      String logPath = new File(logDirectory, "idea_statistics_uploader.log").getAbsolutePath();
      myLogger.addHandler(newAppender(logPath));
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
  private static Handler newAppender(@NotNull String logPath) {
    try {
      @NonNls FileHandler appender = new FileHandler(logPath, false);
      appender.setLevel(Level.ALL);
      appender.setFormatter(new Formatter() {
        @Override
        public String format(LogRecord record) {
          String level = record.getLevel() == Level.WARNING ? "WARN" : record.getLevel().toString();
          String result =  String.format("%1$td/%1$tm %1$tT %2$5s %3$s - %4$s%5$s",
                                         record.getMillis(),
                                         level,
                                         record.getLoggerName(),
                                         record.getMessage(),
                                         System.lineSeparator());
          Throwable thrown = record.getThrown();
          if (thrown != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            thrown.printStackTrace(pw);
            return result + sw;
          }
          return result;
        }
      });
      return appender;
    }
    catch (IOException e) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("Error creating log file: " + e.getMessage());
      return new ConsoleHandler();
    }
  }

  @Override
  public void info(String message) {
    myLogger.info(message);
  }

  @Override
  public void info(String message, Throwable t) {
    myLogger.log(Level.INFO, message, t);
  }

  @Override
  public void warn(String message) {
    myLogger.warning(message);
  }

  @Override
  public void warn(String message, Throwable t) {
    myLogger.log(Level.WARNING, message, t);
  }

  @Override
  public void trace(String message) {
    myLogger.finer(message);
  }

  @Override
  public boolean isTraceEnabled() {
    return myLogger.isLoggable(Level.FINER);
  }
}
