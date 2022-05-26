// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import org.apache.log4j.Level;
import org.apache.log4j.Priority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;

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
    java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
    clearHandlers(rootLogger);
  }

  public static void clearHandlers(java.util.logging.Logger logger) {
    Handler[] handlers = logger.getHandlers();
    for (Handler handler : handlers) {
      logger.removeHandler(handler);
    }
  }
  public static void configureLogFileAndConsole(@NotNull Path logFilePath,
                                                boolean appendToFile,
                                                boolean showDateInConsole,
                                                @Nullable Runnable onRotate) {
    RollingFileHandler fileHandler = new RollingFileHandler(logFilePath, 10_000_000, 12, appendToFile, onRotate);
    fileHandler.setLevel(java.util.logging.Level.FINEST);
    IdeaLogRecordFormatter layout = new IdeaLogRecordFormatter();
    fileHandler.setFormatter(layout);
    java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
    rootLogger.addHandler(fileHandler);

    ConsoleHandler consoleHandler = new ConsoleHandler();
    consoleHandler.setFormatter(new IdeaLogRecordFormatter(layout, showDateInConsole));
    consoleHandler.setLevel(java.util.logging.Level.WARNING);
    rootLogger.addHandler(consoleHandler);
  }
}
