// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

import com.intellij.compilation.charts.events.ModuleChartEvent
import com.intellij.compilation.charts.events.ModuleFinishChartEvent
import com.intellij.compilation.charts.events.ModuleStartChartEvent
import com.intellij.compilation.charts.events.StatisticChartEvent
import com.intellij.compilation.charts.impl.ChartModule
import com.intellij.compilation.charts.impl.ModuleKey
import java.util.NavigableSet
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class UIModel(
  private val hasNewData: () -> Unit,
  private val threadAddedEvent: () -> Unit,
) {
  private val threadsCount = AtomicInteger(0)
  private val threads: MutableMap<Long, Int> = ConcurrentHashMap()
  private val modules: MutableMap<ModuleKey, ChartModuleImpl> = ConcurrentHashMap()
  private val statistics: MutableMap<Class<out StatisticChartEvent>, NavigableSet<StatisticChartEvent>> = ConcurrentHashMap()

  fun add(event: ModuleChartEvent) {
    modules.computeIfAbsent(event.key()) { ChartModuleImpl(event.key()) }
      .add(event)

    if (threads[event.threadId()] == null) {
      threads.computeIfAbsent(event.threadId(), {_ -> threadsCount.andIncrement})

      // finish
      threadAddedEvent()
      hasNewData()
    }
  }

  fun addAll(events: List<ModuleChartEvent>) {
    for (event in events) add(event)
  }

  fun add(event: StatisticChartEvent) {
    statistics.computeIfAbsent(event::class.java) { TreeSet() }
      .add(event)
    // finish
    hasNewData()
  }

  fun modules(): Map<ModuleKey, ChartModule> = modules
  fun statistics(type: Class<out StatisticChartEvent>): NavigableSet<StatisticChartEvent> = statistics[type] ?: TreeSet()
  fun threads(): Map<Long, Int> = threads

  private fun ModuleChartEvent.key(): ModuleKey = ModuleKey(name(), type(), isTest(), threadId())

  private data class ChartModuleImpl(override val key: ModuleKey) : ChartModule {
    private val events: Array<ModuleChartEvent?> = arrayOf(null, null)

    fun add(event: ModuleChartEvent) {
      when (event) {
        is ModuleStartChartEvent -> events[0] = event
        is ModuleFinishChartEvent -> events[1] = event
      }
    }

    override val start: Long
      get() = events[0]?.nanoTime() ?: System.nanoTime()
    override val finish: Long
      get() = events[1]?.nanoTime() ?: System.nanoTime()
  }
}
