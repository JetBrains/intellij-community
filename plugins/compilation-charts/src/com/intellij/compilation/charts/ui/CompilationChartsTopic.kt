// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

import com.intellij.compilation.charts.events.ModuleChartEvent
import com.intellij.compilation.charts.events.StatisticChartEvent
import com.intellij.compilation.charts.impl.CompilationChartsViewModel.Filter
import com.intellij.compilation.charts.impl.ModuleKey
import com.jetbrains.rd.util.reactive.IViewableList
import com.jetbrains.rd.util.reactive.IViewableMap
import kotlinx.collections.immutable.PersistentList

sealed class CompilationChartsTopic<T> {
  object MODULE : CompilationChartsTopic<IViewableMap.Event<ModuleKey, PersistentList<ModuleChartEvent>>>()
  object STATISTIC : CompilationChartsTopic<IViewableList.Event<StatisticChartEvent>>()
  object FILTER : CompilationChartsTopic<Filter>()
}