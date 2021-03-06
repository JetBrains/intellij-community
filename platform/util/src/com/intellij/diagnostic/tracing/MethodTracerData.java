// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.tracing;

import org.jetbrains.annotations.NotNull;

public class MethodTracerData {
  public final @NotNull MethodTracer.TracerId id;
  public final String className;
  public final String methodName;
  public final long invocationCount;
  public final long nonRecursiveCount;
  public final long totalTime;
  public final long maxTime;

  public final long countOnEdt;
  public final long timeOnEdt;
  public final long maxTimeOnEdt;

  public final int maxRecursionDepth;

  public MethodTracerData(@NotNull MethodTracer.TracerId id,
                          String className,
                          String methodName,
                          long invocationCount,
                          long nonRecursiveCount,
                          long totalTime,
                          long maxTime,
                          long countOnEdt,
                          long timeOnEdt,
                          long maxTimeOnEdt,
                          int maxRecursionDepth) {
    this.id = id;
    this.className = className;
    this.methodName = methodName;
    this.invocationCount = invocationCount;
    this.nonRecursiveCount = nonRecursiveCount;
    this.totalTime = totalTime;
    this.maxTime = maxTime;
    this.maxRecursionDepth = maxRecursionDepth;
    this.countOnEdt = countOnEdt;
    this.timeOnEdt = timeOnEdt;
    this.maxTimeOnEdt = maxTimeOnEdt;
  }

  public double getAvgTime() {
    return average(totalTime, nonRecursiveCount);
  }

  public double getAvgTimeEdt() {
    return average(timeOnEdt, countOnEdt);
  }

  private static double average(long total, long count) {
    if (count == 0) {
      return 0;
    }
    double avg = (double)total / count;

    return (double)(Math.round(avg * 100)) / 100;
  }

}
