// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.util.SystemProperties;
import org.apache.log4j.Level;
import org.apache.log4j.Priority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class JulLogger extends Logger {
  @SuppressWarnings("NonConstantLogger") protected final java.util.logging.Logger myLogger;

  public JulLogger(java.util.logging.Logger delegate) {
    myLogger = delegate;
  }

  @Override
  public boolean isDebugEnabled() {
    return myLogger.isLoggable(java.util.logging.Level.FINE);
  }

  @Override
  public boolean isTraceEnabled() {
    return myLogger.isLoggable(java.util.logging.Level.FINER);
  }

  @Override
  public void debug(String message) {
    myLogger.log(java.util.logging.Level.FINE, message);
  }

  @Override
  public void debug(@Nullable Throwable t) {
    myLogger.log(java.util.logging.Level.FINE, "", t);
  }

  @Override
  public void debug(String message, @Nullable Throwable t) {
    myLogger.log(java.util.logging.Level.FINE, message, t);
  }

  @Override
  public void trace(String message) {
    myLogger.log(java.util.logging.Level.FINER, message);
  }

  @Override
  public void trace(@Nullable Throwable t) {
    myLogger.log(java.util.logging.Level.FINER, "", t);
  }

  @Override
  public void info(String message) {
    myLogger.log(java.util.logging.Level.INFO, message);
  }

  @Override
  public void info(String message, @Nullable Throwable t) {
    myLogger.log(java.util.logging.Level.INFO, message, t);
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    myLogger.log(java.util.logging.Level.WARNING, message, t);
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    String fullMessage = details.length > 0 ? message + "\nDetails: " + String.join("\n", details) : message;
    myLogger.log(java.util.logging.Level.SEVERE, fullMessage, t);
  }

  @Override
  public void setLevel(@NotNull LogLevel level) {
    myLogger.setLevel(level.getLevel());
  }

  @Override
  public void setLevel(@NotNull Level level) {
    switch (level.toInt()) {
      case Priority.OFF_INT:
        myLogger.setLevel(java.util.logging.Level.OFF);
        break;

      case Priority.FATAL_INT:
      case Priority.ERROR_INT:
        myLogger.setLevel(java.util.logging.Level.SEVERE);
        break;

      case Priority.WARN_INT:
        myLogger.setLevel(java.util.logging.Level.WARNING);
        break;

      case Priority.INFO_INT:
        myLogger.setLevel(java.util.logging.Level.INFO);
        break;

      case Priority.DEBUG_INT:
        myLogger.setLevel(java.util.logging.Level.FINE);
        break;

      case Level.TRACE_INT:
        myLogger.setLevel(java.util.logging.Level.FINER);
        break;

      case Priority.ALL_INT:
        myLogger.setLevel(java.util.logging.Level.ALL);
        break;
    }
  }

  public static void clearHandlers() {
    clearHandlers(java.util.logging.Logger.getLogger(""));
  }

  public static void clearHandlers(java.util.logging.Logger logger) {
    for (Handler handler : logger.getHandlers()) {
      logger.removeHandler(handler);
    }
  }

  public static void configureLogFileAndConsole(@NotNull Path logFilePath,
                                                boolean appendToFile,
                                                boolean showDateInConsole,
                                                boolean enableConsoleLogger,
                                                @Nullable Runnable onRotate) {
    long limit = 10_000_000;
    String limitProp = System.getProperty("idea.log.limit");
    if (limitProp != null) {
      try {
        limit = Long.parseLong(limitProp);
      }
      catch (NumberFormatException e) {
        // ignore
      }
    }

    int count = 12;
    String countProp = System.getProperty("idea.log.count");
    if (countProp != null) {
      try {
        count = Integer.parseInt(countProp);
      }
      catch (NumberFormatException e) {
        // ignore
      }
    }

    boolean logConsole = SystemProperties.getBooleanProperty("idea.log.console", true);

    java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
    IdeaLogRecordFormatter layout = new IdeaLogRecordFormatter();

    Handler fileHandler = new RollingFileHandler(logFilePath, limit, count, appendToFile, onRotate);
    fileHandler.setFormatter(layout);
    fileHandler.setLevel(java.util.logging.Level.FINEST);
    rootLogger.addHandler(fileHandler);

    if (enableConsoleLogger && logConsole) {
      Handler consoleHandler = new OptimizedConsoleHandler();
      consoleHandler.setFormatter(new IdeaLogRecordFormatter(showDateInConsole, layout));
      consoleHandler.setLevel(java.util.logging.Level.WARNING);
      rootLogger.addHandler(consoleHandler);
    }
  }

  private static final class OptimizedConsoleHandler extends ConsoleHandler {
    @Override
    public void publish(LogRecord record) {
      // checking levels _before_ calling a synchronized method
      if (isLoggable(record)) {
        super.publish(record);
      }
    }
  }
}
