// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PerformanceTestInfo {
  private final ThrowableComputable<Integer, ?> test; // runnable to measure; returns actual input size
  private final int expectedMs;           // millis the test is expected to run
  private final int expectedInputSize;    // size of input the test is expected to process;
  private ThrowableRunnable<?> setup;      // to run before each test
  private int usedReferenceCpuCores = 1;
  private int maxRetries = 4;             // number of retries if performance failed
  private final String what;         // to print on fail
  private boolean adjustForIO;// true if test uses IO, timings need to be re-calibrated according to this agent disk performance
  private boolean adjustForCPU = true;  // true if test uses CPU, timings need to be re-calibrated according to this agent CPU speed
  private boolean useLegacyScaling;

  static {
    // to use JobSchedulerImpl.getJobPoolParallelism() in tests which don't init application
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
  }

  PerformanceTestInfo(@NotNull ThrowableComputable<Integer, ?> test, int expectedMs, int expectedInputSize, @NotNull String what) {
    this.test = test;
    this.expectedMs = expectedMs;
    this.expectedInputSize = expectedInputSize;
    assert expectedMs > 0 : "Expected must be > 0. Was: " + expectedMs;
    assert expectedInputSize > 0 : "Expected input size must be > 0. Was: " + expectedInputSize;
    this.what = what;
  }

  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo setup(@NotNull ThrowableRunnable<?> setup) {
    assert this.setup == null;
    this.setup = setup;
    return this;
  }

  /**
   * Invoke this method if and only if the code under performance tests is using all CPU cores.
   * The "standard" expected time then should be given for a machine which has 8 CPU cores.
   * Actual test expected time will be adjusted according to the number of cores the actual computer has.
   */
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo usesAllCPUCores() { return usesMultipleCPUCores(8); }

  /**
   * Invoke this method if and only if the code under performance tests is using {@code maxCores} CPU cores (or less if the computer has less).
   * The "standard" expected time then should be given for a machine which has {@code maxCores} CPU cores.
   * Actual test expected time will be adjusted according to the number of cores the actual computer has.
   */
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo usesMultipleCPUCores(int maxCores) {
    assert adjustForCPU : "This test configured to be io-bound, it cannot use all cores";
    usedReferenceCpuCores = maxCores;
    return this;
  }

  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo ioBound() {
    adjustForIO = true;
    adjustForCPU = false;
    return this;
  }

  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo attempts(int attempts) {
    this.maxRetries = attempts;
    return this;
  }

  /**
   * @deprecated Enables procedure for nonlinear scaling of results between different machines. This was historically enabled, but doesn't
   * seem to be meaningful, and is known to make results worse in some cases. Consider migration off this setting, recalibrating
   * expected execution time accordingly.
   */
  @Deprecated
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo useLegacyScaling() {
    useLegacyScaling = true;
    return this;
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void assertTiming() {
    if (PlatformTestUtil.COVERAGE_ENABLED_BUILD) return;
    Timings.getStatistics(); // warm-up, measure
    updateJitUsage();

    if (maxRetries == 1) {
      //noinspection CallToSystemGC
      System.gc();
    }
    int initialMaxRetries = maxRetries;

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      CpuUsageData data;
      AtomicInteger actualInputSize;
      try {
        if (setup != null) setup.run();
        PlatformTestUtil.waitForAllBackgroundActivityToCalmDown();
        actualInputSize = new AtomicInteger(expectedInputSize);
        data = CpuUsageData.measureCpuUsage(() -> {
          actualInputSize.set(test.compute());
        });
      }
      catch (Throwable throwable) {
        ExceptionUtil.rethrowUnchecked(throwable);
        throw new RuntimeException(throwable);
      }

      int expectedOnMyMachine = getExpectedTimeOnThisMachine(actualInputSize.get());
      IterationResult iterationResult = data.getIterationResult(expectedOnMyMachine);

      boolean testPassed = iterationResult == IterationResult.ACCEPTABLE || iterationResult == IterationResult.BORDERLINE;
      String logMessage = formatMessage(data, expectedOnMyMachine, actualInputSize.get(), iterationResult, initialMaxRetries);

      if (testPassed) {
        TeamCityLogger.info(logMessage);
        System.out.println("\nSUCCESS: " + logMessage);
        return;
      }
      TeamCityLogger.warning(logMessage, null);
      if (UsefulTestCase.IS_UNDER_TEAMCITY) {
        System.out.println("\nWARNING: " + logMessage);
      }

      JitUsageResult jitUsage = updateJitUsage();
      if (attempt == maxRetries) {
        throw new AssertionFailedError(logMessage);
      }
      if ((iterationResult == IterationResult.DISTRACTED || jitUsage == JitUsageResult.UNCLEAR) && attempt < initialMaxRetries+30 && maxRetries != 1) {
        // completely ignore this attempt (by incrementing maxRetries) and retry (but do no more than 30 extra retries caused by JIT)
        maxRetries++;
      }
      String s = "  " + (maxRetries - attempt) + " " + StringUtil.pluralize("attempt", maxRetries - attempt) + " remain" +
                 (jitUsage == JitUsageResult.UNCLEAR ? " (waiting for JITc; its usage was " + jitUsage + " in this iteration)" : "");
      TeamCityLogger.warning(s, null);
      if (UsefulTestCase.IS_UNDER_TEAMCITY) {
        System.out.println(s);
      }
      //noinspection CallToSystemGC
      System.gc();
    }
  }

  private @NotNull String formatMessage(@NotNull CpuUsageData data,
                                        int expectedOnMyMachine,
                                        int actualInputSize,
                                        @NotNull IterationResult iterationResult,
                                        int initialMaxRetries) {
    long duration = data.durationMs;
    int percentage = (int)(100.0 * (duration - expectedOnMyMachine) / expectedOnMyMachine);
    String colorCode = iterationResult == IterationResult.ACCEPTABLE ? "32;1m" : // green
                       iterationResult == IterationResult.BORDERLINE ? "33;1m" : // yellow
                       "31;1m"; // red
    return
      what+" took \u001B[" + colorCode + Math.abs(percentage) + "% " + (percentage > 0 ? "more" : "less") + " time\u001B[0m than expected" +
      (iterationResult == IterationResult.DISTRACTED && initialMaxRetries != 1 ? " (but JIT compilation took too long, will retry anyway)" : "") +
      "\n  Expected: " + expectedOnMyMachine + "ms (" + StringUtil.formatDuration(expectedOnMyMachine) + ")" +
      "\n  Actual:   " + duration + "ms (" + StringUtil.formatDuration(duration) + ")" +
      (expectedInputSize != actualInputSize ? "\n  (Expected time was adjusted accordingly to input size: expected " + expectedInputSize + ", actual " + actualInputSize + ".)": "") +
      "\n  Timings:  " + Timings.getStatistics() +
      "\n  Threads:  " + data.getThreadStats() +
      "\n  GC stats: " + data.getGcStats() +
      "\n  Process:  " + data.getProcessCpuStats();
  }

  private long lastJitUsage;
  private long lastJitStamp = -1;

  private JitUsageResult updateJitUsage() {
    long timeNow = System.nanoTime();
    long jitNow = CpuUsageData.getTotalCompilationMillis();

    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(timeNow - lastJitStamp);
    if (lastJitStamp >= 0) {
      if (elapsedMillis >= 3_000) {
        if (jitNow - lastJitUsage <= elapsedMillis / 10) {
          return JitUsageResult.DEFINITELY_LOW;
        }
      } else {
        // don't update stamps too frequently,
        // because JIT times are quite discrete: they only change after a compilation is finished,
        // and some compilations take a second or even more
        return JitUsageResult.UNCLEAR;
      }
    }

    lastJitStamp = timeNow;
    lastJitUsage = jitNow;

    return JitUsageResult.UNCLEAR;
  }

  private enum JitUsageResult {DEFINITELY_LOW, UNCLEAR}

  enum IterationResult {
    ACCEPTABLE, // test was completed within specified range
    BORDERLINE, // test barely managed to complete within specified range
    SLOW,       // test was too slow
    DISTRACTED  // CPU was occupied by irrelevant computations for too long (e.g., JIT or GC)
  }

  private int getExpectedTimeOnThisMachine(int actualInputSize) {
    int expectedOnMyMachine = (int) (((long)expectedMs) * actualInputSize / expectedInputSize);
    if (adjustForCPU) {
      int coreCountUsedHere = usedReferenceCpuCores < 8
                              ? Math.min(JobSchedulerImpl.getJobPoolParallelism(), usedReferenceCpuCores)
                              : JobSchedulerImpl.getJobPoolParallelism();
      expectedOnMyMachine *= usedReferenceCpuCores;
      expectedOnMyMachine = adjust(expectedOnMyMachine, Timings.CPU_TIMING, Timings.REFERENCE_CPU_TIMING, useLegacyScaling);
      expectedOnMyMachine /= coreCountUsedHere;
    }
    if (adjustForIO) {
      expectedOnMyMachine = adjust(expectedOnMyMachine, Timings.IO_TIMING, Timings.REFERENCE_IO_TIMING, useLegacyScaling);
    }
    return expectedOnMyMachine;
  }

  private static int adjust(int expectedOnMyMachine, long thisTiming, long referenceTiming, boolean useLegacyScaling) {
    if (useLegacyScaling) {
      double speed = 1.0 * thisTiming / referenceTiming;
      double delta = speed < 1
                     ? 0.9 + Math.pow(speed - 0.7, 2)
                     : 0.45 + Math.pow(speed - 0.25, 2);
      expectedOnMyMachine *= delta;
      return expectedOnMyMachine;
    }
    else {
      return (int)(expectedOnMyMachine * thisTiming / referenceTiming);
    }
  }
}
