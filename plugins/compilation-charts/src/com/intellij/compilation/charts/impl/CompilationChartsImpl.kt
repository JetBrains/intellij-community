// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.impl

import com.intellij.compilation.charts.CompilationCharts
import com.intellij.compilation.charts.events.ChartEvent
import com.intellij.compilation.charts.events.ModuleChartEvent
import com.intellij.compilation.charts.events.ModuleFinishChartEvent
import com.intellij.compilation.charts.events.ModuleStartChartEvent
import com.intellij.compilation.charts.events.StatisticChartEvent
import com.intellij.compilation.charts.impl.CompilationChartsViewModel.Filter
import com.intellij.compilation.charts.ui.CompilationChartsTopic
import com.intellij.compilation.charts.ui.CompilationChartsTopic.*
import com.intellij.compilation.charts.ui.CompilationChartsView
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NlsContexts
import com.jetbrains.rd.framework.impl.RdList
import com.jetbrains.rd.framework.impl.RdMap
import com.jetbrains.rd.framework.impl.RdProperty
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IViewableList
import com.jetbrains.rd.util.reactive.IViewableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import javax.swing.JComponent

class CompilationChartsImpl(
  private val project: Project,
  private val disposable: Disposable,
  private val eventDeclarations: Map<Class<out StatisticChartEvent>, EventDeclaration>,
) : CompilationCharts, CompilationChartsViewModel {

  private val lifetime: Lifetime = disposable.createLifetime()
  private val modules: Modules = Modules(Long.MAX_VALUE, 0, RdMap())

  private val statistics: Map<Class<out StatisticChartEvent>, RdList<StatisticChartEvent>> =
    eventDeclarations.map { (k, v) -> k to RdList<StatisticChartEvent>() }.toMap()

  private val state = CompilationChartsState()

  private val _component: CompilationChartsView by lazy {
    CompilationChartsView(project, this)
  }

  override fun getComponent(): JComponent = _component

  override fun dispose() {
  }

  override fun put(event: ChartEvent) {
    when (event) {
      is ModuleStartChartEvent, is ModuleFinishChartEvent -> modules.add(event)
      is StatisticChartEvent -> eventDeclarations[event::class.java]?.let { declaration ->
        if (declaration.maximum() < event.max()) eventDeclarations[event::class.java]?.maximum(event.max())
        statistics[event::class.java]?.add(event)
      }
    }

    if (state.start > event.nanoTime()) state.start = event.nanoTime()
    if (state.end < event.nanoTime()) state.end = event.nanoTime()
  }

  override fun changeStatistic(): EventDeclaration {
    state.statisticIndex.set((state.statisticIndex.value + 1) % eventDeclarations.size)
    return eventDeclarations.getByIndex(state.statisticIndex.value).value
  }

  override fun statisticType(): EventDeclaration =
    eventDeclarations.getByIndex(state.statisticIndex.value).value

  fun <K, V> Map<K, V>.getByIndex(index: Int): Map.Entry<K, V> {
    entries.forEachIndexed { idx, entry -> if (idx == index) return entry }
    return entries.first()
  }

  override fun filter(text: String): Int {
    val words = text.split(" ").filter { it.isNotBlank() }.map { it.trim() }
    state.filter.set(state.filter.value.text(words))
    if (words.isEmpty()) {
      return -1
    }
    else {
      val count = modules.events.count { state.filter.value.test(it.key) }
      if (count == modules.events.count()) {
        return -1
      }
      else {
        return count
      }
    }
  }

  override fun changeProduction(): Boolean {
    state.filter.set(state.filter.value.production(!state.filter.value.production()))
    return state.filter.value.production()
  }

  override fun changeTest(): Boolean {
    state.filter.set(state.filter.value.test(!state.filter.value.test()))
    return state.filter.value.test()
  }

  override fun project(): Project = project

  override fun dependenciesFor(moduleName: String): Boolean {
    val dependenciesFor = state.filter.value.dependenciesFor()
    if (dependenciesFor != null && dependenciesFor == moduleName) {
      state.filter.set(state.filter.value.dependenciesFor(null))
      return false
    }
    val module = ModuleManager.getInstance(project()).findModuleByName(moduleName) ?: return false

    val dependencies: MutableList<String> = ArrayList()
    dependencies.add(moduleName)
    ModuleRootManager.getInstance(module).orderEntries().forEach { entry -> dependencies.add(entry.presentableName) }
    state.filter.set(state.filter.value.dependenciesFor(moduleName, { key -> dependencies.contains(key.name) }))
    return true
  }

  override fun disposable(): Disposable = disposable

  @Suppress("UNCHECKED_CAST")
  override fun <T> subscribe(topic: CompilationChartsTopic<T>, handler: (T) -> Unit) {
    when (topic) {
      MODULE -> modules.events.advise(lifetime, handler as (IViewableMap.Event<ModuleKey, PersistentList<ModuleChartEvent>>) -> Unit)
      STATISTIC -> for (statistic in statistics) {
        statistic.value.advise(lifetime, handler as (IViewableList.Event<StatisticChartEvent>) -> Unit)
      }
      FILTER -> state.filter.advise(lifetime, handler as (Filter) -> Unit)
    }
  }

  private class CompilationChartsState() {
    val statisticIndex = RdProperty<Int>(-1)
    val filter = RdProperty<Filter>(FilterImpl())
    var start: Long = 0
    var end: Long = 0
  }

  private data class Modules(var start: Long, var end: Long, val events: RdMap<ModuleKey, PersistentList<ModuleChartEvent>>) {
    fun add(event: ModuleChartEvent) {
      if (start > event.nanoTime()) start = event.nanoTime()
      if (end < event.nanoTime()) end = event.nanoTime()
      events.compute(event.key()) { _, list -> list?.add(event) ?: persistentListOf(event) }
    }
    fun ModuleChartEvent.key(): ModuleKey = ModuleKey(name(), type(), isTest(), threadId())
  }

  data class FilterImpl(
    val text: List<String> = listOf(), val production: Boolean = true,
    val test: Boolean = true, val dependenciesFor: FilterDependenciesFor? = null,
  ) : Filter {
    override fun text(text: List<String>): Filter = FilterImpl(text, production, test, null)
    override fun production(production: Boolean): Filter = FilterImpl(text, production, test, null)
    override fun production(): Boolean = production
    override fun test(test: Boolean): Filter = FilterImpl(text, production, test, null)
    override fun test(): Boolean = test
    override fun dependenciesFor(name: String?, predicate: (((ModuleKey) -> Boolean))?): Filter {
      if (name == null || predicate == null) return FilterImpl(text, production, test, null)
      else return FilterImpl(text, production, test, FilterDependenciesFor(name, predicate))
    }

    override fun isEmpty(): Boolean = text.isEmpty() && dependenciesFor == null

    override fun dependenciesFor(): String? = dependenciesFor?.name

    override fun test(module: ModuleKey): Boolean {
      if (dependenciesFor != null && !dependenciesFor.predicate.invoke(module)) return false

      if (text.isNotEmpty()) {
        if (!text.all {
            @Suppress("HardCodedStringLiteral")
            module.name.contains(it)
          }) return false
      }

      if (module.test) {
        if (!test) return false
      }
      else {
        if (!production) return false
      }
      return true
    }
  }

  data class FilterDependenciesFor(val name: String, val predicate: ((ModuleKey) -> Boolean))
}

data class ModuleKey(@NlsContexts.Label val name: String, val type: String, val test: Boolean, val thread: Long)