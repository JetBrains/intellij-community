// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.exception;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.containers.IntIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Reports exceptions thrown from frequently called methods (e.g. {@link Component#paint(Graphics)}),
 * so that instead of polluting the log with hundreds of {@link Logger#error(Throwable) LOG.errors} it prints the error message
 * and the stacktrace once in a while.
 */
public final class FrequentErrorLogger {
  private static final int REPORT_EVERY_NUM = 1024;
  private final IntIntHashMap myReportedIssues = new IntIntHashMap(10, 0); // stacktrace hash -> number of reports
  @NotNull private final Logger myLogger;

  @NotNull
  public static FrequentErrorLogger newInstance(@NotNull Logger logger) {
    return new FrequentErrorLogger(logger);
  }

  private FrequentErrorLogger(@NotNull Logger logger) {
    myLogger = logger;
    assert (REPORT_EVERY_NUM & (REPORT_EVERY_NUM-1)) == 0 : "make REPORT_EVERY_NUM power of two";
  }

  public void error(@NotNull @NonNls String message, @NotNull Throwable t) {
    report(t, () -> myLogger.error(message, t));
  }

  public void error(@NotNull @NonNls String message, @NotNull Throwable t, Attachment... attachments) {
    report(t, () -> myLogger.error(message, t, attachments));
  }

  public void info(@NotNull @NonNls String message, @NotNull Throwable t) {
    report(t, () -> myLogger.info(message, t));
  }

  private void report(@NotNull Throwable t, @NotNull Runnable writeToLog) {
    int hash = ThrowableInterner.computeHashCode(t);
    int reportedTimes;
    synchronized (myReportedIssues) {
      reportedTimes = myReportedIssues.addTo(hash, 1);
    }
    if ((reportedTimes & (REPORT_EVERY_NUM -1)) == 0) {
      writeToLog.run();
    }
  }
}
