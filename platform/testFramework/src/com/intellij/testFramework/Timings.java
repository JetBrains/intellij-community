/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework;

import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.util.SystemInfo;

/**
 * @author peter
 */
@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public class Timings {
  public static final long CPU_TIMING;
  public static final long IO_TIMING;

  /**
   * Measured on dual core p4 3HZ 1gig ram
   */
  public static final long REFERENCE_CPU_TIMING = 200;
  public static final long REFERENCE_IO_TIMING = 100;

  static {
    long cpuTiming = CpuTimings.calcStableCpuTiming();
    if (SystemInfo.isJavaVersionAtLeast(11, 0, 0)) {
      // on JBR 11, the code for CPU timings executes much faster, while most tests take roughly the same time
      // so we correct for the difference until we have a more robust timing calculation
      cpuTiming = cpuTiming * 54 / 31;
    }
    CPU_TIMING = cpuTiming;
    IO_TIMING = IoTimings.calcIoTiming();
  }

  /**
   * @param value the value (e.g. number of iterations) which needs to be adjusted according to my machine speed
   * @param isParallelizable true if the test load is scalable with the CPU cores
   * @return value calibrated according to this machine speed. For slower machine, lesser value will be returned
   */
  public static int adjustAccordingToMySpeed(int value, boolean isParallelizable) {
    return Math.max(1, (int)(1.0 * value * REFERENCE_CPU_TIMING / CPU_TIMING) / 8 * (isParallelizable ? JobSchedulerImpl.getJobPoolParallelism() : 1));
  }

  public static String getStatistics() {
    return String.format("CPU=%d (%d%% reference CPU), I/O=%d (%d%% reference IO), %d cores",
                         CPU_TIMING, CPU_TIMING * 100 / REFERENCE_CPU_TIMING,
                         IO_TIMING, IO_TIMING * 100 / REFERENCE_IO_TIMING, Runtime.getRuntime().availableProcessors());
  }
}
