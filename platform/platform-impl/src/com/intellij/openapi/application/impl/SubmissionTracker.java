// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class SubmissionTracker {
  private static final Logger LOG = Logger.getInstance(SubmissionTracker.class);
  @VisibleForTesting
  static final String TOO_MANY_SUBMISSIONS =
    "Too many non-blocking read actions submitted at once. " +
    "Please use coalesceBy, BoundedTaskExecutor or another way of limiting the number of concurrently running threads.";

  private final AtomicInteger myCount = new AtomicInteger();

  /** Not-null if we're tracking submissions to provide diagnostics */
  @Nullable private volatile Map<String, Integer> myTraces;

  @Nullable
  String preventTooManySubmissions() {
    Map<String, Integer> traces = myTraces;
    int currentCount = myCount.incrementAndGet();
    if (currentCount > 100) {
      if (traces == null) {
        myTraces = ContainerUtil.newConcurrentMap();
      } else {
        Integer count = traces.get(callerTrace());
        if (count != null && count > 10) {
          LOG.error(TOO_MANY_SUBMISSIONS);
        }
        else if (currentCount % 127 == 0) {
          reportTooManyUnidentifiedSubmissions(traces);
        }
      }
    }
    if (traces != null) {
      String trace = callerTrace();
      traces.merge(trace, 1, Integer::sum);
      return trace;
    }
    return null;
  }

  private String callerTrace() {
    return StreamEx
      .of(new Throwable().getStackTrace())
      .dropWhile(ste -> ste.getClassName().contains("NonBlockingReadAction") || ste.getClassName().equals(getClass().getName()))
      .limit(10)
      .joining("\n");
  }

  void unregisterSubmission(@Nullable String startTrace) {
    myCount.decrementAndGet();
    Map<String, Integer> traces = myTraces;
    if (startTrace != null && traces != null) {
      traces.compute(startTrace, (__, i) -> i == null || i.intValue() == 1 ? null : i - 1);
    }
  }

  private static void reportTooManyUnidentifiedSubmissions(Map<String, Integer> traces) {
    String mostFrequentTraces = EntryStream
      .of(traces)
      .sortedByInt(e -> -e.getValue())
      .map(e -> e.getValue() + " occurrences of " + e.getKey())
      .limit(10)
      .joining("\n\n");

    if (LOG.isDebugEnabled()) {
      LOG.debug(mostFrequentTraces);
    }
    Attachment attachment = new Attachment("diagnostic.txt", mostFrequentTraces);
    attachment.setIncluded(true);
    LOG.error(TOO_MANY_SUBMISSIONS, attachment);
  }

}
