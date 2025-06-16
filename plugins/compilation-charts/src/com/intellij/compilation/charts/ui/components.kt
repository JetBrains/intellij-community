// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

import com.intellij.compilation.charts.events.StatisticChartEvent
import com.intellij.compilation.charts.impl.ChartModule
import com.intellij.compilation.charts.impl.ModuleKey
import com.intellij.compilation.charts.ui.Colors.FILTERED_ALPHA
import com.intellij.compilation.charts.ui.Colors.NO_ALPHA
import com.intellij.compilation.charts.ui.Settings.Block.MIN_SIZE
import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.util.text.Formats
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.util.NavigableSet
import java.util.TreeSet
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

interface ChartComponent {
  fun background(g2d: ChartGraphics, settings: ChartSettings)
  fun component(g2d: ChartGraphics, settings: ChartSettings)
  fun width(settings: ChartSettings): Int = Int.MAX_VALUE
  fun height(): Double
}

class ChartProgress(private val zoom: Zoom, internal val state: ChartModel) : ChartComponent {
  var selected: ModuleKey? = null

  var height: Double = 25.5

  private lateinit var block: ModuleBlock
  private lateinit var background: ModuleBackground

  internal lateinit var bracket: Rectangle2D

  fun block(init: ModuleBlock.() -> Unit) {
    block = ModuleBlock().apply(init)
  }

  class ModuleBlock {
    var border: Double = 2.0
    var padding: Double = 1.0
    lateinit var color: (ChartModule) -> Color
    lateinit var outline: (ChartModule) -> Color
    lateinit var selected: (ChartModule) -> Color
  }

  fun background(init: ModuleBackground.() -> Unit) {
    background = ModuleBackground().apply(init)
  }

  class ModuleBackground {
    lateinit var color: (Int) -> Color
  }

  override fun width(settings: ChartSettings): Int {
    var start = bracket.x + bracket.width
    var end = bracket.x

    state.model.forEach { (_, module) ->
      if (compareWithViewport(module.start, module.finish, settings, zoom, bracket) == 0) {
        val rect = getRectangle(module, state.threads[module.key.thread] ?: state.threads.size, settings)
        start = min(rect.x, start)
        end = max(rect.x + rect.width, end)
      }
    }
    return if (start < end) (end - bracket.x).roundToInt() else 0
  }

  override fun height(): Double = bracket.height
  fun rows(): Int = state.threads.size

  override fun background(g2d: ChartGraphics, settings: ChartSettings) {
    g2d.withColor(settings.background) {
      fill(bracket)
    }
    state.threads.forEach { (_, position) ->
      val cell = Rectangle2D.Double(bracket.x, height * position + bracket.y, bracket.width, height)
      g2d.withColor(background.color(position)) {
        fill(cell)
      }
    }
  }

  override fun component(g2d: ChartGraphics, settings: ChartSettings) {
    g2d.withAntialiasing {
      drawChart(state, settings)
    }
  }

  private fun ChartGraphics.drawChart(
    state: ChartModel,
    settings: ChartSettings,
  ) {
    state.model.forEach { (_, module) ->
      if (compareWithViewport(module.start, module.finish, settings, zoom, bracket) == 0 &&
          !isSmall(module, state)) {
        val rect = getRectangle(module, state.threads[module.key.thread] ?: state.threads.size, settings)

        settings.mouse.module(rect, module.key, mutableMapOf(
          "duration" to NlsMessages.formatDurationApproximate((module.finish - module.start) / 1_000_000),
          "name" to module.key.name,
          "type" to module.key.type,
          "test" to module.key.test.toString()
        ))

        val alpha = if (state.filter.test(module.key)) NO_ALPHA else FILTERED_ALPHA
        withColor(block.color(module).alpha(settings.background, alpha)) { // module
          fill(rect)
        }
        withColor(block.color(module, selected)) { // module border
          if (alpha == NO_ALPHA) draw(rect)
        }
        create().withColor(settings.font.color.alpha(settings.background, alpha)) {
          withFont(UIUtil.getLabelFont(settings.font.size)) { // name
            clip(rect)
            drawString(" ${module.key.name}", rect.x.toFloat(), (rect.y + (height - block.padding * 2) / 2 + fontMetrics().ascent / 2).toFloat())
          }
        }
      }
    }
  }

  private fun ModuleBlock.color(event: ChartModule, selected: ModuleKey?): Color {
    if (selected == event.key) {
      return selected(event)
    }
    else {
      return outline(event)
    }
  }

  private fun isSmall(event: ChartModule, state: ChartModel): Boolean {
    if (state.filter.isEmpty()) {
      return zoom.toPixels(event.finish) - zoom.toPixels(event.start) < MIN_SIZE
    }
    else {
      return false
    }
  }

  private fun getRectangle(event: ChartModule, thread: Int, settings: ChartSettings): Rectangle2D {
    val x0 = zoom.toPixels(event.start - settings.duration.from)
    val x1 = zoom.toPixels(event.finish - settings.duration.from)
    return Rectangle2D.Double(x0, (thread * height), x1 - x0, height)
  }
}

class ChartUsage(private val zoom: Zoom, internal val state: UsageModel) : ChartComponent {
  lateinit var format: BiFunction<Long, Long, String>
  lateinit var color: UsageColor

  internal lateinit var bracket: Rectangle2D

  fun color(init: UsageColor.() -> Unit) {
    color = UsageColor().apply(init)
  }

  class UsageColor {
    lateinit var background: JBColor
    lateinit var border: JBColor
  }

  override fun background(g2d: ChartGraphics, settings: ChartSettings) {
    g2d.withColor(settings.background) {
      fill(bracket)
    }
    g2d.withColor(settings.line.color) {
      draw(Line2D.Double(bracket.x, bracket.y, bracket.x + bracket.width, bracket.y))
    }
  }

  override fun component(g2d: ChartGraphics, settings: ChartSettings) {
    val filtered = filterData(state.model, settings)
    val path = path(filtered, settings) ?: return
    val border = border(filtered, settings) ?: return

    g2d.withColor(color.background) {
      fill(path)
    }
    g2d.withColor(color.border) {
      g2d.withStroke(BasicStroke(Settings.Usage.BORDER)) {
        draw(border)
      }
    }
  }

  fun drawPoint(g2d: ChartGraphics, data: StatisticChartEvent, settings: ChartSettings) {
    val border = 5
    val height = bracket.height - border
    val y0 = bracket.y + height + border

    val radius = 4
    val (x, y) = getXY(data, settings, y0, height)
    g2d.withColor(color.border) {
      fillOval(x.roundToInt() - radius, y.roundToInt() - radius, radius * 2, radius * 2)
    }
    g2d.withColor(color.background) {
      drawOval(x.roundToInt() - radius, y.roundToInt() - radius, radius * 2, radius * 2)
    }
    g2d.withColor(settings.font.color) {
      val text = format.apply(data.nanoTime(), data.value().roundToLong())
      val bounds = getStringBounds(text)
      drawString(text, x.roundToInt() - bounds.width.roundToInt() / 2, maxOf(y.roundToInt() - bounds.height.roundToInt(), bounds.height.roundToInt() + 30))
    }
  }

  override fun width(settings: ChartSettings): Int {
    if (state.model.isEmpty()) return 0
    val (end, _) = getXY(state.model.last(), settings, 0.0, 0.0)
    return (end - bracket.x).roundToInt()
  }

  override fun height(): Double = bracket.height

  private fun filterData(data: NavigableSet<StatisticChartEvent>, settings: ChartSettings): NavigableSet<StatisticChartEvent> {
    val filtered = TreeSet<StatisticChartEvent>()
    var before: StatisticChartEvent? = null
    var after: StatisticChartEvent? = null
    for (statistic in data) {
      when (compareWithViewport(statistic.nanoTime(), statistic.nanoTime(), settings, zoom, bracket)) {
        0 -> filtered.add(statistic)
        -1 -> before = statistic
        1 -> after = statistic
      }
      if (after != null) break
    }
    if (before != null) filtered.add(before)
    if (after != null) filtered.add(after)
    if (filtered.isNotEmpty()) {
      (0..4).forEach { i ->
        data.lower(filtered.first())?.let { first -> filtered.add(first) }
        data.higher(filtered.last())?.let { last -> filtered.add(last) }
      }
    }
    return filtered
  }

  private fun border(data: NavigableSet<StatisticChartEvent>, settings: ChartSettings): Path2D? =
    path(data, settings, { _, _, _, _ -> }, { _, _, _ -> })

  private fun path(data: NavigableSet<StatisticChartEvent>, settings: ChartSettings): Path2D? {
    val setFirstPoint: (Path2D, Double, Double, Double) -> Unit = { path, x0, y0, data0 ->
      path.moveTo(x0, y0)
      path.moveTo(x0, data0)
    }
    val setLastPoint: (Path2D, Double, Double) -> Unit = { path, x0, y0 ->
      path.currentPoint?.let { last ->
        path.lineTo(last.x, y0)
        path.lineTo(x0, y0)
      }
      path.closePath()
    }
    return path(data, settings, setFirstPoint, setLastPoint)
  }

  private fun path(
    data: NavigableSet<StatisticChartEvent>, settings: ChartSettings,
    before: (Path2D, Double, Double, Double) -> Unit,
    after: (Path2D, Double, Double) -> Unit,
  ): Path2D? {
    if (data.isEmpty()) return null
    val neighborhood = DoubleArray(8) { Double.NaN }
    val border = 5
    val height = bracket.height - border
    val y0 = bracket.y + height + border
    val path = Path2D.Double()

    val (x0, data0) = getXY(data.first(), settings, y0, height)
    before(path, x0, y0, data0)

    data.forEach { statistic ->
      val (px, py) = getXY(statistic, settings, y0, height)
      path.currentPoint ?: path.moveTo(px, py) // if the first point
      neighborhood.shiftLeftByTwo(px, py)
      path.curveTo(neighborhood)
    }
    neighborhood.shiftLeftByTwo(Double.NaN, Double.NaN)
    path.curveTo(neighborhood)

    after(path, x0, y0)
    return path
  }

  private fun getXY(statistic: StatisticChartEvent, settings: ChartSettings, y0: Double, height: Double): Pair<Double, Double> =
    zoom.toPixels(statistic.nanoTime() - settings.duration.from) to
      y0 - (statistic.value() / state.maximum * height)

  private fun DoubleArray.shiftLeftByTwo(first: Double, second: Double) {
    for (j in 2 until size) {
      this[j - 2] = this[j]
    }
    this[size - 2] = first
    this[size - 1] = second
  }
}

class ChartAxis(private val zoom: Zoom) : ChartComponent {
  var stroke: FloatArray = floatArrayOf(5f, 5f)
  var distance: Int = 250
  var count: Int = 10
  var height: Double = 0.0
  var padding: Int = 2

  internal lateinit var bracket: Rectangle2D

  override fun background(g2d: ChartGraphics, settings: ChartSettings) {
    g2d.withColor(settings.background) {
      fill(bracket)
    }
    g2d.withColor(settings.line.color) {
      draw(Line2D.Double(bracket.x, bracket.y, bracket.x + bracket.width, bracket.y))
    }
  }

  override fun component(g2d: ChartGraphics, settings: ChartSettings) {
    g2d.withAntialiasing {
      val size = UIUtil.getFontSize(settings.font.size) + padding

      val from = (bracket.x.roundToInt() / distance) * distance
      val to = from + bracket.width.roundToInt() + bracket.x.roundToInt() % distance

      withColor(Colors.LINE.alpha(0.75)) {
        for (x in from..to step distance) {
          // big axis
          withStroke(BasicStroke(1.5F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, this@ChartAxis.stroke, 0.0f)) {
            draw(Line2D.Double(x.toDouble(), 0.0, x.toDouble(), bracket.y))
          }
          for (x1 in x..x + distance step distance / count) {
            // additional axis
            draw(Line2D.Double(x1.toDouble(), bracket.y, x1.toDouble(), bracket.y + (size / 2)))
          }
        }
      }
      withColor(settings.font.color) {
        val step = zoom.toDuration(distance)
        val trim = if (TimeUnit.NANOSECONDS.toMinutes(step) > 2) 60_000
        else if (TimeUnit.NANOSECONDS.toMinutes(step) >= 1) 1_000
        else if (TimeUnit.NANOSECONDS.toSeconds(step) > 2) 1_000
        else 1

        withFont(UIUtil.getLabelFont(settings.font.size)) {
          for (x in from..to step distance) {
            val time = Formats.formatDuration((TimeUnit.NANOSECONDS.toMillis(zoom.toDuration(x)) / trim) * trim)
            drawString(time, x + padding, (bracket.y + size).roundToInt())
          }
        }
      }
    }
  }

  override fun height(): Double = bracket.height
}