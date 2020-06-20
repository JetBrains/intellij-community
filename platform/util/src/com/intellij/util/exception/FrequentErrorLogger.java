// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.exception;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reports exceptions thrown from frequently called methods (e.g. {@link Component#paint(Graphics)}),
 * so that instead of polluting the log with hundreds of {@link Logger#error(Throwable) LOG.errors} it prints the error message
 * and the stacktrace once in a while.
 */
public final class FrequentErrorLogger {

  private static final int REPORT_EVERY_NUM = 1000;
  @NotNull private final Map<Integer, Integer> ourReportedIssues = new ConcurrentHashMap<>(); // stacktrace hash -> number of reports
  @NotNull private final Logger myLogger;

  @NotNull
  public static FrequentErrorLogger newInstance(@NotNull Logger logger) {
    return new FrequentErrorLogger(logger);
  }

  private FrequentErrorLogger(@NotNull Logger logger) {
    myLogger = logger;
  }

  public void error(@NotNull String message, @NotNull Throwable t) {
    report(t, () -> myLogger.error(message, t));
  }

  public void error(@NotNull String message, @NotNull Throwable t, Attachment... attachments) {
    report(t, () -> myLogger.error(message, t, attachments));
  }

  public void info(@NotNull String message, @NotNull Throwable t) {
    report(t, () -> myLogger.info(message, t));
  }

  private void report(@NotNull Throwable t, @NotNull Runnable writeToLog) {
    int hash = ThrowableInterner.computeHashCode(t);
    Integer reportedTimes = ourReportedIssues.get(hash);
    if (reportedTimes == null || reportedTimes > REPORT_EVERY_NUM) {
      writeToLog.run();
      ourReportedIssues.put(hash, 1);
    }
    else {
      ourReportedIssues.put(hash, reportedTimes + 1);
    }
  }
}
