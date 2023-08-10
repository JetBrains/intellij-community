// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class WorkingTimeMeasurer {
  private final long myTimeLimit;
  private final long myStart;
  @Nullable private static final ThreadMXBean ourThreadMXBean;

  static {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    ourThreadMXBean = bean.isCurrentThreadCpuTimeSupported() ? bean : null;
  }

  private static long getCurrentTime() {
    return ourThreadMXBean != null ? ourThreadMXBean.getCurrentThreadUserTime() : System.nanoTime();
  }

  public WorkingTimeMeasurer(long nanoLimit) {
    myTimeLimit = nanoLimit;
    myStart = getCurrentTime();
  }

  public boolean isTimeOver() {
    return getCurrentTime() - myStart > myTimeLimit;
  }
}
