// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

import com.intellij.compilation.charts.events.StatisticChartEvent
import com.intellij.compilation.charts.impl.CompilationChartsViewModel
import com.intellij.compilation.charts.impl.ModuleKey
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil.FontSize
import java.awt.Color
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import java.awt.geom.Rectangle2D
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun charts(zoom: Zoom, init: Charts.() -> Unit): Charts {
  return Charts(zoom).apply(init)
}

class Charts(private val zoom: Zoom) {
  private val model: DataModel = DataModel(this)
  internal val progress: ChartProgress = ChartProgress(zoom, model.chart)
  internal lateinit var usage: ChartUsage
  internal lateinit var axis: ChartAxis
  internal var settings: ChartSettings = ChartSettings()

  fun settings(init: ChartSettings.() -> Unit) {
    settings = ChartSettings().apply(init)
  }

  fun progress(init: ChartProgress.() -> Unit) {
    progress.apply(init)
  }

  fun usage(init: ChartUsage.() -> Unit) {
    usage.apply(init)
  }

  fun axis(init: ChartAxis.() -> Unit) {
    axis = ChartAxis(zoom).apply(init)
  }

  fun draw(g2d: ChartGraphics, init: Charts.(Double, Double) -> Unit) {
    init(max(zoom.toPixels(MaxSize(progress, settings).width), width().toDouble()),
         axis.bracket.run { y + height })

    val components = listOf(progress, usage, axis)
    components.forEach { it.background(g2d, settings) }
    components.forEach { it.component(g2d, settings) }
  }

  fun update(init: Charts.() -> Unit) {
    init()
  }

  fun model(function: DataModel.() -> Unit): Charts {
    model.function()
    settings.duration.from = min(model.chart.start, model.usage.start)
    settings.duration.to = max(model.chart.end, model.usage.end)
    return this
  }

  fun width(): Int {
    return listOf(progress, usage, axis).minOfOrNull { it.width(settings) } ?: 0
  }

  fun height(): Double = listOf(progress, usage, axis).sumOf { it.height() }

  fun clips(area: Rectangle2D.Double) {
    val size = MaxSize(progress, settings)
    progress.bracket = Rectangle2D.Double(area.x,
                                          0.0,
                                          area.width,
                                          size.height)
    usage.bracket = Rectangle2D.Double(area.x,
                                       size.height,
                                       progress.bracket.width,
                                       max(progress.height * 3, area.height - progress.bracket.height - axis.height))
    axis.bracket = Rectangle2D.Double(area.x,
                                      progress.bracket.height + usage.bracket.height,
                                      progress.bracket.width,
                                      axis.height)
  }
}

class ChartSettings {
  internal lateinit var font: ChartFont
  internal lateinit var mouse: CompilationChartsModuleInfo
  var background: Color = JBColor.WHITE
  internal val duration: ChartDuration = ChartDuration()

  internal var line: ChartLine = ChartLine()

  fun font(init: ChartFont.() -> Unit) {
    font = ChartFont().apply(init)
  }

  fun line(init: ChartLine.() -> Unit) {
    line = ChartLine().apply(init)
  }

  class ChartFont {
    var size: FontSize = FontSize.NORMAL
    var color: Color = JBColor.DARK_GRAY
  }

  class ChartDuration {
    var from: Long = Long.MAX_VALUE
    var to: Long = 0
  }

  class ChartLine {
    var color: Color = JBColor.LIGHT_GRAY
    var size: Int = 1
  }
}

internal data class MaxSize(val width: Double, val height: Double) {
  constructor(width: Long, height: Double) : this(width.toDouble(), height)
  constructor(progress: ChartProgress, settings: ChartSettings) : this(with(settings.duration) { to - from }, (progress.rows()) * progress.height)
}

class CompilationChartsModuleInfo(
  private val vm: CompilationChartsViewModel,
  component: CompilationChartsDiagramsComponent,
) : MouseAdapter() {
  private val components = ConcurrentHashMap<ModuleKey, ModuleIndex>()
  private val hint = CompilationChartsHint(vm.project(), component, vm.disposable())

  override fun mouseClicked(e: MouseEvent) {
    val name = search(e.point)?.key?.name ?: return
    vm.dependenciesFor(name)
  }

  override fun mouseMoved(e: MouseEvent) {
    val module = search(e.point)
    if (hint.isInside(e.point)) return

    if (module == null) {
      hint.close()
    }
    else if (module != hint.module()) {
      hint.open(module, e, 750)
    }
  }

  fun clear(): Unit = components.clear()

  fun module(rect: Rectangle2D, key: ModuleKey, info: Map<String, String>) {
    components.put(key, ModuleIndex(rect, key, info))
  }

  private fun search(point: Point): ModuleIndex? = components.values.firstOrNull { it.contains(point) }
}

class CompilationChartsUsageInfo(val component: CompilationChartsDiagramsComponent, val charts: Charts, val zoom: Zoom) : MouseMotionListener {
  var statistic: StatisticChartEvent? = null
  override fun mouseDragged(e: MouseEvent) {
  }

  override fun mouseMoved(e: MouseEvent) {
    val point = e.point
    if (point.y >= charts.usage.bracket.y &&
        point.y <= charts.usage.bracket.y + charts.usage.bracket.height) {
      statistic = search(point)
      if (statistic != null) {
        component.smartDraw(false, false)
      }
    }
    else {
      if (statistic != null) {
        statistic = null
        component.smartDraw(false, false)
      }
    }
  }

  fun draw(g2d: ChartGraphics) {
    statistic?.let { stat ->
      charts.usage.drawPoint(g2d, stat, charts.settings)
    }
  }

  private fun search(point: Point): StatisticChartEvent? {
    if (charts.usage.state.model.isEmpty()) return null
    var statistic = charts.usage.state.model.first()
    var currentDistance = abs(zoom.toPixels(statistic.nanoTime() - charts.settings.duration.from) - point.x)
    var lastDistance = currentDistance
    charts.usage.state.model.forEach { stat ->
      val x = zoom.toPixels(stat.nanoTime() - charts.settings.duration.from)
      if (abs(point.x - x) < currentDistance) {
        statistic = stat
        lastDistance = currentDistance
        currentDistance = abs(point.x - x)
      }
      else if (lastDistance < currentDistance) {
        return statistic
      }
    }
    return statistic
  }
}

data class ModuleIndex(
  val x0: Double, val x1: Double,
  val y0: Double, val y1: Double,
  val key: ModuleKey,
  val info: Map<String, String>,
) {
  constructor(rect: Rectangle2D, key: ModuleKey, info: Map<String, String>) : this(
    rect.x, rect.x + rect.width,
    rect.y, rect.y + rect.height,
    key, info
  )

  fun contains(point: Point, border: Int = 0): Boolean =
    x0 - border <= point.x &&
    x1 + border >= point.x &&
    y0 - border <= point.y &&
    y1 + border >= point.y

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ModuleIndex

    if (x0 != other.x0) return false
    if (y0 != other.y0) return false
    if (key != other.key) return false

    return true
  }

  override fun hashCode(): Int = Objects.hash(x0, y0, key)
}