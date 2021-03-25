// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class TimeoutCachedValue<T> implements Supplier<T> {
  private final long myTimeoutMs;
  @NotNull private final Supplier<? extends T> myValueSupplier;
  private long myLastCalcTime;
  private T myCache;

  public TimeoutCachedValue(long timeout, @NotNull TimeUnit unit, @NotNull Supplier<? extends T> valueSupplier) {
    myValueSupplier = valueSupplier;
    myTimeoutMs = unit.toMillis(timeout);
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

  public synchronized boolean hasUpToDateValue() {
    return myTimeoutMs > System.currentTimeMillis() - myLastCalcTime;
  }
}
