// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import io.opentelemetry.api.trace.Span;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.vcs.log.util.StopWatchScopeKt.StopWatchScope;

public final @NonNls class StopWatch {

  private static final Logger LOG = Logger.getInstance(StopWatch.class);

  private static final String[] UNIT_NAMES = new String[]{"s", "m", "h"};
  private static final long[] UNITS = new long[]{1, 60, 60 * 60};
  private static final String M_SEC_FORMAT = "%03d";

  private final long myStartTime;

  private final Span mySpan;
  private final @NotNull String myOperation;
  private final @NotNull Map<VirtualFile, Long> myDurationPerRoot;

  private StopWatch(@NotNull String operation) {
    mySpan = TelemetryManager.getInstance().getTracer(StopWatchScope).spanBuilder(operation).startSpan();
    myOperation = operation;
    myStartTime = System.currentTimeMillis();
    myDurationPerRoot = new HashMap<>();
  }

  public static @NotNull StopWatch start(@NonNls @NotNull String operation) {
    return new StopWatch(operation);
  }

  public void rootCompleted(@NotNull VirtualFile root) {
    long totalDuration = System.currentTimeMillis() - myStartTime;
    long duration = totalDuration - sum(myDurationPerRoot.values());
    myDurationPerRoot.put(root, duration);
  }

  private static long sum(@NotNull Collection<Long> durations) {
    long sum = 0;
    for (Long duration : durations) {
      sum += duration;
    }
    return sum;
  }

  public void report() {
    report(LOG);
  }

  public void report(@NotNull Logger logger) {
    mySpan.end();
    String message = myOperation + " took " + formatTime(System.currentTimeMillis() - myStartTime);
    if (myDurationPerRoot.size() > 1) {
      message += "\n" + StringUtil.join(myDurationPerRoot.entrySet(),
                                        entry -> "    " + entry.getKey().getName() + ": " + formatTime(entry.getValue()), "\n");
    }
    logger.debug(message);
  }

  /**
   * 1h 1m 1.001s
   */
  public static @NotNull String formatTime(long time) {
    if (time < 1000 * UNITS[0]) {
      return time + "ms";
    }
    String result = "";
    long remainder = time / 1000;
    long msec = time % 1000;
    for (int i = UNITS.length - 1; i >= 0; i--) {
      if (remainder < UNITS[i]) continue;

      long quotient = remainder / UNITS[i];
      remainder = remainder % UNITS[i];

      if (i == 0) {
        result += quotient + (msec == 0 ? "" : "." + String.format(M_SEC_FORMAT, msec)) + UNIT_NAMES[i];
      }
      else {
        result += quotient + UNIT_NAMES[i];
        if (remainder != 0 || msec != 0) {
          result += " ";
          if (remainder == 0) {
            result += "0." + String.format(M_SEC_FORMAT, msec) + UNIT_NAMES[0];
          }
        }
      }
    }

    return result;
  }
}
