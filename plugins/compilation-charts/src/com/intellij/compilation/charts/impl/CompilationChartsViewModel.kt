// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.impl

import com.intellij.compilation.charts.ui.CompilationChartsTopic
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.util.function.Predicate

interface CompilationChartsViewModel {
  fun changeStatistic(): EventDeclaration
  fun changeProduction(): Boolean
  fun changeTest(): Boolean

  fun filter(text: String): Int
  fun project(): Project
  fun dependenciesFor(moduleName: String): Boolean

  fun <T> subscribe(topic: CompilationChartsTopic<T>, handler: (T) -> Unit)
  fun disposable(): Disposable

  fun statisticType(): EventDeclaration

  interface Filter : Predicate<ModuleKey> {
    fun text(text: List<String>): Filter

    fun production(production: Boolean): Filter
    fun production(): Boolean

    fun test(test: Boolean): Filter
    fun test(): Boolean
    fun isEmpty(): Boolean

    fun dependenciesFor(name: String?, predicate: (((ModuleKey) -> Boolean))? = null): Filter
    fun dependenciesFor(): String?
  }
}

interface ChartModule {
  val key: ModuleKey
  val start: Long
  val finish: Long
}
