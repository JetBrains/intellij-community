// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

import com.intellij.compilation.charts.impl.CompilationChartsViewModel
import com.intellij.compilation.charts.ui.CompilationChartsTopic.*
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rd.util.reactive.IViewableList
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants

class CompilationChartsView(project: Project, vm: CompilationChartsViewModel) : BorderLayoutPanel(), UiDataProvider {
  private val diagrams: CompilationChartsDiagramsComponent
  private val rightAdhesionScrollBarListener: RightAdhesionScrollBarListener

  init {
    val zoom = Zoom()
    val scrollType = AutoScrollingType()

    val scroll = object : JBScrollPane() {
      override fun createViewport(): JViewport = CompilationChartsViewport(scrollType)
    }.apply {
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
      border = JBUI.Borders.empty()
      viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE
      name = "compilation-charts-scroll-pane"
    }
    rightAdhesionScrollBarListener = RightAdhesionScrollBarListener(scroll.viewport, zoom, scrollType)
    scroll.horizontalScrollBar.addAdjustmentListener(rightAdhesionScrollBarListener)
    diagrams = CompilationChartsDiagramsComponent(vm, zoom, scroll.viewport).apply {
      addMouseWheelListener(rightAdhesionScrollBarListener)
      name = "compilation-charts-diagrams-component"
      isFocusable = true
    }

    scroll.setViewportView(diagrams)

    val panel = ActionPanel(project, vm, diagrams, scroll.viewport)
    panel.border = JBUI.Borders.customLineBottom(JBColor.border())
    addToTop(panel)
    addToCenter(scroll)

    vm.subscribe(MODULE) { module ->
      val modules = module.newValueOpt ?: return@subscribe
      diagrams.uiModel.addAll(modules)
      //panel.updateLabel(vm.modules.get().keys, vm.filter.value) todo
    }

    vm.subscribe(STATISTIC) { statistic ->
      if (statistic !is IViewableList.Event.Add) return@subscribe
      diagrams.uiModel.add(statistic.newValue)
    }

    vm.subscribe(FILTER) { filter ->
      diagrams.filter = filter
      diagrams.smartDraw(true, false)
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[COMPILATION_CHARTS_VIEW_KEY] = this
  }

  internal fun scrollToEnd() {
    rightAdhesionScrollBarListener.scrollToEnd()
  }

  internal fun zoom(zoomType: ZoomEvent) {
    when (zoomType) {
      ZoomEvent.IN -> rightAdhesionScrollBarListener.increase()
      ZoomEvent.OUT -> rightAdhesionScrollBarListener.decrease()
      ZoomEvent.RESET -> rightAdhesionScrollBarListener.reset()
    }
    diagrams.smartDraw(true, false)
  }
}

enum class ZoomEvent {
  IN,
  OUT,
  RESET
}


internal val COMPILATION_CHARTS_VIEW_KEY = DataKey.create<CompilationChartsView>("CompilationChartsView")