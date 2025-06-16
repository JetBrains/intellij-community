// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.jps

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.util.registry.Registry

class CompilationChartsBuildParametersProvider: BuildProcessParametersProvider() {
  override fun getVMArguments(): List<String> = listOf("-D${CompilationChartsProjectActivity.Companion.COMPILATION_CHARTS_KEY}=${Registry.`is`(
    CompilationChartsProjectActivity.Companion.COMPILATION_CHARTS_KEY)}")
}