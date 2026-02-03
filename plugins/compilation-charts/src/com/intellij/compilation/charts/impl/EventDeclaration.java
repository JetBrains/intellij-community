// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.impl;

import com.intellij.compilation.charts.CompilationChartsFactory.EventColor;
import com.intellij.compilation.charts.CompilationChartsFactory.EventLayout;
import com.intellij.compilation.charts.events.StatisticChartEvent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class EventDeclaration {
  private final Class<? extends StatisticChartEvent> clazz;
  private final @Nls String title;
  private final EventLayout layout;
  private final EventColor color;
  private double maximum;

  public EventDeclaration(@NotNull Class<? extends StatisticChartEvent> clazz, @Nls String title, @NotNull EventColor color, @NotNull EventLayout layout, double maximum) {
    this.clazz = clazz;
    this.title = title;
    this.layout = layout;
    this.color = color;
    this.maximum = maximum;
  }

  public EventDeclaration(@NotNull Class<? extends StatisticChartEvent> clazz, @Nls String title, @NotNull EventColor color, @NotNull EventLayout layout) {
    this(clazz, title, color, layout, 100.0);
  }

  public @NotNull Class<? extends StatisticChartEvent> clazz() {
    return clazz;
  }

  public @Nls String title() {
    return title;
  }

  public EventLayout layout() {
    return layout;
  }

  public double maximum() {
    return maximum;
  }

  public @NotNull EventColor color() {
    return color;
  }

  public void maximum(double maximum) {
    this.maximum = maximum;
  }
}
