// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.charts

import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Area
import java.lang.Double.min
import javax.swing.SwingConstants
import kotlin.math.abs
import kotlin.math.max

class BarDataset<T: Number>: Dataset<T>() {

  var stacked: Boolean = false
  var values: Iterable<T>
    get() = data
    set(value) {
      data = value
    }
  var showValues : ((T) -> String)? = null

  init {
    fillColor = lineColor
  }

  companion object {
    @JvmStatic
    fun <T: Number> of(vararg values: T): BarDataset<T> = BarDataset<T>().apply {
      data = values.toList()
    }
  }

}

abstract class BarChart<T: Number>: GridChartWrapper<Int, T>() {

  var datasets: List<BarDataset<T>> = mutableListOf()
  override val ranges: Grid<Int, T> = Grid()
  var gap: Int = 10
  var space: Int = -1

  override fun paintComponent(g: Graphics2D) {

    val xy = findMinMax()
    if (xy.isInitialized) {
      val datasetCount = getDatasetCount()
      val grid = g.create(margins.left, margins.top, gridWidth, gridHeight) as Graphics2D
      paintGrid(grid, g, xy)
      var index = 0
      datasets.forEach { dataset ->
        dataset.paintDataset(index, datasetCount, grid, xy)
        if (!dataset.stacked) {
          index++
        }
      }
      grid.dispose()
    }
  }

  private fun getDatasetCount() =  datasets.map { if (it.stacked) 0 else 1 }.sum()

  override fun findMinMax(): MinMax<Int, T> = if (ranges.isInitialized) ranges else ranges * (ranges + MinMax()).apply {
    datasets.forEach { it.data.forEachIndexed { i, v -> process(i, v) } }
  }

  protected abstract fun BarDataset<T>.paintDataset(datasetIndex: Int, datasetCount: Int, g: Graphics2D, xy: MinMax<Int, T>)

}

class HorizontalBarChart<T: Number> : BarChart<T>() {

  override fun BarDataset<T>.paintDataset(datasetIndex: Int, datasetCount: Int, g: Graphics2D, xy: MinMax<Int, T>) {
    assert(xy.xMin == 0) { "Int value must start with 0" }

    val columns = xy.xMax + 1
    val max = max(xy.yMax.toDouble(), 0.0)
    val min = min(xy.yMin.toDouble(), 0.0)
    if (max == min) {
      return
    }

    val cb = g.clipBounds

    val z = cb.height / (max - min)
    val axis = (z * abs(min)).toInt()

    data.forEachIndexed { column, value ->

      var h = (value.toDouble() * z).toInt()
      var y = cb.height - h - axis

      val groupW = cb.width / columns - gap
      val groupX = column * cb.width / columns + gap / 2

      val space = if (space < 0) max(1, groupW / 10) else space
      val w = max(1, (groupW - space * (datasetCount - 1)) / datasetCount)
      val x = groupX + datasetIndex * w + space * datasetIndex

      if (h < 0) {
        y += h
        h = abs(h)
      }
      lineColor?.let {
        g.paint = lineColor
        g.drawRect(x, y, w, h)
      }
      fillColor?.let {
        g.paint = it
        g.fillRect(x, y, w, h)
      }

      if (stacked) {
        val area = Area(g.clip)
        area.subtract(Area(Rectangle(x, y, w, h)))
        g.clip = area
      }

      showValues?.let { toString ->
        val str = toString(value)
        val bounds = g.fontMetrics.getStringBounds(str, g)
        g.drawString(str, x + (w - bounds.width.toInt()) / 2, y - 5)
      }
    }
  }

  override fun findGridLineX(gl: GridLine<Int, T, *>, x: Int): Double {
    return gridWidth * ((x + 1).toDouble() - gl.xy.xMin.toDouble()) / (gl.xy.xMax.toDouble() - gl.xy.xMin.toDouble() + 1)
  }

  override fun findY(xy: MinMax<Int, T>, y: T): Double {
    val isRanged = xy.yMin.toDouble() <= 0.0 && 0.0 <= xy.yMax.toDouble()
    val yMin = if (isRanged || xy.yMin.toDouble() < 0) xy.yMin.toDouble() else 0.0
    val yMax = if (isRanged || xy.yMax.toDouble() > 0) xy.yMax.toDouble() else 0.0
    val height = height - (margins.top + margins.bottom)
    return height - height * (y.toDouble() - yMin) / (yMax - yMin)
  }

  override fun findGridLabelOffset(line: GridLine<*, *, *>, g: Graphics2D): Coordinates<Double, Double> {
    val onLineAlignment = super.findGridLabelOffset(line, g)
    if (line.orientation == SwingConstants.VERTICAL) {
      val width = width - (margins.left + margins.right)
      val columnWidth = width / (line.xy.xMax.toDouble() - line.xy.xMin.toDouble() + 1)
      return (onLineAlignment.x + columnWidth / 2) to onLineAlignment.y
    } else {
      return onLineAlignment
    }
  }
}