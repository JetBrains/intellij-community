// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.events;

import org.jetbrains.annotations.NotNull;

public interface StatisticChartEvent extends ChartEvent  {
  double value();
  double max();
  @NotNull String type();
}
