// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThrowableRunnable;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class PerformanceTestInfo {
  private final ThrowableRunnable<?> test; // runnable to measure
  private final int expectedMs;           // millis the test is expected to run
  private ThrowableRunnable<?> setup;      // to run before each test
  private int usedReferenceCpuCores = 1;
  private int attempts = 4;             // number of retries if performance failed
  private final String what;         // to print on fail
  private boolean adjustForIO = false;// true if test uses IO, timings need to be re-calibrated according to this agent disk performance
  private boolean adjustForCPU = true;  // true if test uses CPU, timings need to be re-calibrated according to this agent CPU speed
  private boolean useLegacyScaling;

  static {
    // to use JobSchedulerImpl.getJobPoolParallelism() in tests which don't init application
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
  }

  PerformanceTestInfo(@NotNull ThrowableRunnable test, int expectedMs, @NotNull String what) {
    this.test = test;
    this.expectedMs = expectedMs;
    assert expectedMs > 0 : "Expected must be > 0. Was: " + expectedMs;
    this.what = what;
  }

  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo setup(@NotNull ThrowableRunnable setup) {
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

  /**
   * @deprecated tests are CPU-bound by default, so no need to call this method.
   */
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  @Deprecated
  public PerformanceTestInfo cpuBound() {
    adjustForIO = false;
    adjustForCPU = true;
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
    this.attempts = attempts;
    return this;
  }

  /**
   * @deprecated Enables procedure for nonlinear scaling of results between different machines. This was historically enabled, but doesn't
   * seem to be meaningful, and is known to make results worse in some cases. Consider migration off this setting, recalibrating
   * expected execution time accordingly.
   */
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo useLegacyScaling() {
    useLegacyScaling = true;
    return this;
  }

  public void assertTiming() {
    if (PlatformTestUtil.COVERAGE_ENABLED_BUILD) return;
    Timings.getStatistics(); // warm-up, measure

    if (attempts == 1) {
      System.gc();
    }

    while (true) {
      attempts--;
      CpuUsageData data;
      try {
        if (setup != null) setup.run();
        PlatformTestUtil.waitForAllBackgroundActivityToCalmDown();
        data = CpuUsageData.measureCpuUsage(test);
      }
      catch (RuntimeException | Error throwable) {
        throw throwable;
      }
      catch (Throwable throwable) {
        throw new RuntimeException(throwable);
      }
      long duration = data.durationMs;

      int expectedOnMyMachine = getExpectedTimeOnThisMachine();

      // Allow 10% more in case of test machine is busy.
      double acceptableChangeFactor = attempts == 1 ? 1.1 : 1.0;
      int percentage = (int)(100.0 * (duration - expectedOnMyMachine) / expectedOnMyMachine);
      String colorCode = duration < expectedOnMyMachine ? "32;1m" : // green
                         duration < expectedOnMyMachine * acceptableChangeFactor ? "33;1m" : // yellow
                         "31;1m"; // red
      String logMessage = String.format(
        "%s took \u001B[%s%d%% %s time\u001B[0m than expected" +
        "\n  Expected: %sms (%s)" +
        "\n  Actual:   %sms (%s)" +
        "\n  Timings:  %s" +
        "\n  Threads:  %s" +
        "\n  GC stats: %s",
        what, colorCode, Math.abs(percentage), percentage > 0 ? "more" : "less",
        expectedOnMyMachine, StringUtil.formatDuration(expectedOnMyMachine),
        duration, StringUtil.formatDuration(duration),
        Timings.getStatistics(),
        data.getThreadStats(),
        data.getGcStats());

      if (duration < expectedOnMyMachine) {
        TeamCityLogger.info(logMessage);
        System.out.println("\nSUCCESS: " + logMessage);
      }
      else if (duration < expectedOnMyMachine * acceptableChangeFactor) {
        TeamCityLogger.warning(logMessage, null);
        System.out.println("\nWARNING: " + logMessage);
      }
      else {
        // try one more time
        if (attempts == 0) {
          throw new AssertionFailedError(logMessage);
        }
        System.gc();
        System.gc();
        System.gc();
        String s = logMessage + "\n  " + attempts + " attempts remain";
        TeamCityLogger.warning(s, null);
        if (UsefulTestCase.IS_UNDER_TEAMCITY) {
          System.err.println(s);
        }
        continue;
      }
      break;
    }
  }

  private int getExpectedTimeOnThisMachine() {
    int expectedOnMyMachine = expectedMs;
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
