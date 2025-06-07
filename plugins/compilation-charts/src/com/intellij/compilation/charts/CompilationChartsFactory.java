// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts;

import com.intellij.compilation.charts.events.StatisticChartEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

public abstract class CompilationChartsFactory {
  public static CompilationChartsFactory getInstance() {
    return ApplicationManager.getApplication().getService(CompilationChartsFactory.class);
  }

  public abstract CompilationChartsFactory registerEvent(@NotNull Class<? extends StatisticChartEvent> clazz,
                                                         @Nls String title,
                                                         @NotNull EventColor color,
                                                         @NotNull EventLayout layout);

  public abstract CompilationCharts create(@NotNull Project project, @NotNull Disposable disposable);

  public interface EventLayout extends BiFunction<Long, Long, String> {}
  public record EventColor(JBColor background, JBColor border) {}
}
