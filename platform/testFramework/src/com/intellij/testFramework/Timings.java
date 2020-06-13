// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.concurrency.JobSchedulerImpl;
import org.jetbrains.annotations.Range;

/**
 * @author peter
 */
@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public final class Timings {
  public static final long CPU_TIMING;
  public static final long IO_TIMING;

  /**
   * Measured on dual core p4 3HZ 1gig ram
   */
  public static final long REFERENCE_CPU_TIMING = 200;
  public static final long REFERENCE_IO_TIMING = 100;

  static {
    CPU_TIMING = CpuTimings.calcStableCpuTiming();
    IO_TIMING = IoTimings.calcIoTiming();
  }

  /**
   * @param value the value (e.g. number of iterations) which needs to be adjusted according to my machine speed
   * @param isParallelizable true if the test load is scalable with the CPU cores
   * @return value calibrated according to this machine speed. For slower machine, lesser value will be returned
   */
  public static @Range(from = 1, to = Integer.MAX_VALUE) int adjustAccordingToMySpeed(int value, boolean isParallelizable) {
    return Math.max(1, (int)(1.0 * value * REFERENCE_CPU_TIMING / CPU_TIMING) / 8 * (isParallelizable ? JobSchedulerImpl.getJobPoolParallelism() : 1));
  }

  public static String getStatistics() {
    return String.format("CPU=%d (%d%% reference CPU), I/O=%d (%d%% reference IO), %d cores",
                         CPU_TIMING, CPU_TIMING * 100 / REFERENCE_CPU_TIMING,
                         IO_TIMING, IO_TIMING * 100 / REFERENCE_IO_TIMING, Runtime.getRuntime().availableProcessors());
  }
}
