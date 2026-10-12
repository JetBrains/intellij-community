// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class WorkingTimeMeasurer {
  private final long myTimeLimit;
  private final long myStart;
  private static final @Nullable ThreadMXBean ourThreadMXBean;

  static {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    ourThreadMXBean = bean.isCurrentThreadCpuTimeSupported() ? bean : null;
  }

  private static long getCurrentTime() {
    // ThreadMXBean returns -1 for virtual threads. Use System.nanoTime() so their timeouts can expire.
    return ourThreadMXBean != null && !Thread.currentThread().isVirtual()
           ? ourThreadMXBean.getCurrentThreadUserTime()
           : System.nanoTime();
  }

  public WorkingTimeMeasurer(long nanoLimit) {
    myTimeLimit = nanoLimit;
    myStart = getCurrentTime();
  }

  public boolean isTimeOver() {
    return getCurrentTime() - myStart > myTimeLimit;
  }
}
