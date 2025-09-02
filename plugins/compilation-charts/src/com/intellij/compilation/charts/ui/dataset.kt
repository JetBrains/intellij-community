// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

import com.intellij.compilation.charts.events.StatisticChartEvent
import com.intellij.compilation.charts.impl.ChartModule
import com.intellij.compilation.charts.impl.CompilationChartsImpl.FilterImpl
import com.intellij.compilation.charts.impl.CompilationChartsViewModel.Filter
import com.intellij.compilation.charts.impl.ModuleKey
import java.util.NavigableSet
import java.util.TreeSet
import kotlin.math.max
import kotlin.math.min

data class DataModel(private val charts: Charts) {
  internal val chart: ChartModel = ChartModel()
  internal lateinit var usage: UsageModel
  fun progress(init: ChartModel.() -> Unit) {
    chart.init()
  }

  fun usage(type: ChartUsage, init: UsageModel.() -> Unit) {
    charts.usage = type
    usage = type.state
    usage.init()
  }
}

class ChartModel {
  internal var model: Map<ModuleKey, ChartModule> = mapOf()
    set(value) {
      field = HashMap(value).also { data ->
        data.values.forEach { module ->
          start = min(start, module.start)
          end = max(end, module.finish)
        }
      }
    }

  internal var threads: Map<Long, Int> = mapOf()
    set(value) {
      field = HashMap(value)
    }
  internal var filter: Filter = FilterImpl()
  internal var currentTime: Long = 0
  internal var start: Long = Long.MAX_VALUE
  internal var end: Long = Long.MIN_VALUE
}

class UsageModel {
  internal var model: NavigableSet<StatisticChartEvent> = TreeSet()
    set(value) {
      field = TreeSet(value).also { data ->
        data.forEach {
          start = min(start, it.nanoTime())
          end = max(end, it.nanoTime())
        }
      }
    }

  internal var start: Long = Long.MAX_VALUE
  internal var end: Long = Long.MIN_VALUE
  internal var maximum: Double = 0.0
}