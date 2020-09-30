// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class StatisticsCachingSupplier<T> implements Supplier<T> {
  private final long myTimeoutMs;
  @NotNull private final Supplier<? extends T> myValueSupplier;
  private long myLastCalcTime;
  private T myCache;

  public StatisticsCachingSupplier(@NotNull Supplier<? extends T> valueSupplier, long timeoutMs) {
    myTimeoutMs = timeoutMs;
    myValueSupplier = valueSupplier;
  }

  @Override
  public synchronized T get() {
    if (hasUpToDateValue()) {
      return myCache;
    }
    myCache = myValueSupplier.get();
    myLastCalcTime = System.currentTimeMillis();
    return myCache;
  }

  private synchronized boolean hasUpToDateValue() {
    return myTimeoutMs > System.currentTimeMillis() - myLastCalcTime;
  }
}
