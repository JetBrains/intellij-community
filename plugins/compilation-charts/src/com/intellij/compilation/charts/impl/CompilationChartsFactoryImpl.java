// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.impl;

import com.intellij.compilation.charts.CompilationCharts;
import com.intellij.compilation.charts.CompilationChartsFactory;
import com.intellij.compilation.charts.events.StatisticChartEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public class CompilationChartsFactoryImpl extends CompilationChartsFactory {
  private final Map<Class<? extends StatisticChartEvent>, EventDeclaration> eventDeclarations = new LinkedHashMap<>();

  @Override
  public CompilationChartsFactory registerEvent(@NotNull Class<? extends StatisticChartEvent> clazz,
                                                @Nls String title,
                                                @NotNull EventColor color,
                                                @NotNull EventLayout layout) {
    eventDeclarations.put(clazz, new EventDeclaration(clazz, title, color, layout));
    return this;
  }


  @Override
  public CompilationCharts create(@NotNull Project project, @NotNull Disposable disposable) {
    return new CompilationChartsImpl(project, disposable, eventDeclarations);
  }
}
