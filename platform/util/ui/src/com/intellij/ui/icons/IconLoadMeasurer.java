// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

public final class IconLoadMeasurer {
  private final ImageType type;

  private final AtomicInteger counter = new AtomicInteger();
  private final AtomicInteger totalTime = new AtomicInteger();

  public IconLoadMeasurer(@NotNull ImageType type) {
    this.type = type;
  }

  public int getCounter() {
    return counter.get();
  }

  public int getTotalTime() {
    return totalTime.get();
  }

  @NotNull
  public ImageType getType() {
    return type;
  }

  public void add(int duration) {
    counter.incrementAndGet();
    totalTime.updateAndGet(current -> current + duration);
  }
}
