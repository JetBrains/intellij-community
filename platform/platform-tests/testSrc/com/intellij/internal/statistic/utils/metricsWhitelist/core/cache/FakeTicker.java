// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.core.cache;

import com.google.common.base.Ticker;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class FakeTicker extends Ticker {
  private final AtomicLong myNanos = new AtomicLong();

  @Override
  public long read() {
    return myNanos.getAndAdd(0);
  }

  public void advance(long time, @NotNull TimeUnit timeUnit) {
    myNanos.addAndGet(timeUnit.toNanos(time));
  }
}
