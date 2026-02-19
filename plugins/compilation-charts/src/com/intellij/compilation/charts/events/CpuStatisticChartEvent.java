// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.events;

import org.jetbrains.annotations.NotNull;

public record CpuStatisticChartEvent(long nanoTime,
                                     double value,
                                     double max) implements StatisticChartEvent {
  @Override
  public @NotNull String type() {
    return "cpu";
  }
}
