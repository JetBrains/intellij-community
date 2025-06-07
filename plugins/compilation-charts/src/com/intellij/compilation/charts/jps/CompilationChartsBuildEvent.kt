// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.jps

import com.intellij.build.BuildViewManager
import com.intellij.build.events.BuildEventPresentationData
import com.intellij.build.events.PresentableBuildEvent
import com.intellij.build.events.impl.AbstractBuildEvent
import com.intellij.compilation.charts.CompilationCharts
import com.intellij.compilation.charts.CompilationChartsBundle
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import javax.swing.Icon
import javax.swing.JComponent

internal class CompilationChartsBuildEvent(val view: BuildViewManager,
                                  val buildId: Any,
                                  val chart: CompilationCharts
) :
  AbstractBuildEvent(Any(), buildId, System.currentTimeMillis(), CompilationChartsBundle.message("charts.tab.name")),
  PresentableBuildEvent {

  private val console: CompilationChartsExecutionConsole by lazy { CompilationChartsExecutionConsole(chart) }

  override fun getPresentationData(): BuildEventPresentationData = CompilationChartsPresentationData(console)

  private class CompilationChartsPresentationData(private val component: ExecutionConsole) : BuildEventPresentationData {
    override fun getNodeIcon(): Icon = AllIcons.Actions.Profile

    override fun getExecutionConsole(): ExecutionConsole = component

    override fun consoleToolbarActions(): ActionGroup? = null
  }

  private class CompilationChartsExecutionConsole(val chart: CompilationCharts) : ExecutionConsole {
    override fun dispose() {
    }

    override fun getComponent(): JComponent = chart.component
    override fun getPreferredFocusableComponent(): JComponent = chart.component
  }
}