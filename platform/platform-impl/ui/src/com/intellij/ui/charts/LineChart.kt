// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.charts

import java.awt.BasicStroke
import java.awt.BasicStroke.CAP_BUTT
import java.awt.BasicStroke.JOIN_ROUND
import java.awt.Graphics2D
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.util.*
import kotlin.math.hypot
import kotlin.math.min

/**
 * Simple Line Chart.
 *
 * Has options:
 * <ul>
 *   <li><b>stepped</b> — can be set to LineStepped.(NONE|BEFORE|AFTER)
 *   <li><b>stacked</b> — if <code>true</code> area under chart's line subtracts from result graphic (every next chart cannot paint on this area anymore)
 *   <li><b>stroke</b> — set custom stroke for the line
 * </ul>
 */
abstract class LineDataset<X: Number, Y: Number>: Dataset<Coordinates<X, Y>>() {
  var stepped: LineStepped = LineStepped.NONE
  var stacked: Boolean = false
  var stroke: BasicStroke = BasicStroke(1.5f, CAP_BUTT, JOIN_ROUND)
  var smooth: Boolean = false

  var modificationFirst: Boolean = false
    set(value) {
      field = value
      data = (if (value) LinkedList() else mutableListOf<Coordinates<X, Y>>()).apply {
        data.forEach { this@LineDataset.add(it) }
      }
    }

  fun find(x: X): Y? = data.find { it.x == x }?.y

  companion object {

    @JvmStatic fun <T: Number> of(vararg values: T): CategoryLineDataset<T> = CategoryLineDataset<T>().apply {
      addAll(values.mapIndexed(::Coordinates).toList())
    }

    @JvmStatic fun <X: Number, Y: Number> of(xs: Array<X>, ys: Array<Y>): XYLineDataset<X, Y> = XYLineDataset<X, Y>().apply {
      addAll(Array(min(xs.size, ys.size)) { i -> Coordinates(xs[i], ys[i]) }.toList())
    }

    @JvmStatic fun <X: Number, Y: Number> of(vararg points: Coordinates<X, Y>): XYLineDataset<X, Y> = XYLineDataset<X, Y>().apply {
      addAll(points.toList())
    }
  }
}

/**
 * Type of stepped line:
 *
 * * NONE — line continuously connects every line
 * * BEFORE — line changes value before connection to another point
 * * AFTER — line changes value after connection to another point
 */
enum class LineStepped {
  NONE, BEFORE, AFTER
}

/**
 * Default 2-dimensional dataset for function like f(x) = y.
 */
open class XYLineDataset<X: Number, Y: Number> : LineDataset<X, Y>() {
}

/**
 * Default category dataset, that is specific type of XYLineDataset, when x in range of (0..<value count>).
 */
open class CategoryLineDataset<X: Number> : LineDataset<Int, X>() {
  var values: Iterable<X>
    get() = data.map { it.y }
    set(value) {
      data = value.mapIndexed(::Coordinates)
    }
}

/**
 * Base chart component.
 *
 * For drawing uses GeneralPath from AWT library.
 *
 * Has options:
 *
 * * gridColor
 * * borderPainted — if <code>true</code> draws a border around the chart and respects margins
 * * ranges — grid based range, that holds all information about grid painting. Has minimal and maximum values for graphic.
 */
abstract class LineChart<X: Number, Y: Number, D: LineDataset<X, Y>>: GridChartWrapper<X, Y>() {

  var datasets: List<D> = mutableListOf()

  companion object {

    @JvmStatic fun <T: Number, D: CategoryLineDataset<T>> of(vararg values: D): CategoryLineChart<T> = CategoryLineChart<T>().apply {
      datasets = mutableListOf(*values)
    }

    @JvmStatic fun <X: Number, Y: Number, D: XYLineDataset<X, Y>> of(vararg values: D): XYLineChart<X, Y> = XYLineChart<X, Y>().apply {
      datasets = mutableListOf(*values)
    }

    @JvmStatic fun <T: Number> of(vararg values: T): CategoryLineChart<T> = CategoryLineChart<T>().apply {
      datasets = mutableListOf(LineDataset.of(*values))
    }

    @JvmStatic fun <X: Number, Y: Number> of(vararg points: Coordinates<X, Y>): XYLineChart<X, Y> = XYLineChart<X, Y>().apply {
      datasets = mutableListOf(LineDataset.of(*points))
    }
  }

  var borderPainted: Boolean = false
  override val ranges: Grid<X, Y> = Grid()

  override fun paintComponent(g: Graphics2D) {
    val gridWidth = width - (margins.left + margins.right)
    val gridHeight = height - (margins.top + margins.bottom)

    if (borderPainted) {
      g.color = gridColor
      g.drawRect(margins.left, margins.top, gridWidth, gridHeight)
    }

    val xy = findMinMax()
    if (xy.isInitialized) {
      val grid = g.create(margins.left, margins.top, gridWidth, gridHeight) as Graphics2D
      paintGrid(grid, g, xy)
      datasets.forEach {
        it.paintDataset(grid, xy)
      }
      grid.dispose()
    }
  }

  override fun findMinMax(): MinMax<X, Y> = if (ranges.isInitialized) ranges else ranges * (ranges + MinMax()).apply {
    datasets.forEach { it.data.forEach(::process) }
  }

  private fun D.paintDataset(g: Graphics2D, xy: MinMax<X, Y>) {
    val path = Path2D.Double()
    lateinit var first: Point2D

    val bounds = g.clipBounds

    g.paint = lineColor
    g.stroke = stroke

    // set small array to store 4 points of values
    val useSplines = smooth && stepped == LineStepped.NONE
    val neighborhood = DoubleArray(8) { Double.NaN }

    data.forEachIndexed { i, (x, y) ->
      val px = findX(xy, x)
      val py = findY(xy, y)
      neighborhood.shiftLeftByTwo(px, py)
      if (i == 0) {
        first = Point2D.Double(px, py)
        path.moveTo(px, py)
      } else {
        if (!useSplines) {
          when (stepped) {
            LineStepped.AFTER -> path.lineTo(neighborhood[4], py)
            LineStepped.BEFORE -> path.lineTo(px, neighborhood[5])
            else -> {}
          }
          path.lineTo(px, py)
        } else if (i > 1) {
          path.curveTo(neighborhood)
        }
      }
    }

    // last step to draw tail of graphic, when spline is used
    if (useSplines) {
      neighborhood.shiftLeftByTwo(Double.NaN, Double.NaN)
      path.curveTo(neighborhood)
    }

    g.paint = lineColor
    g.stroke = stroke
    g.draw(path)

    // added some points
    path.currentPoint?.let { last ->
      path.lineTo(last.x, bounds.height + 1.0)
      path.lineTo(first.x, bounds.height + 1.0)
      path.closePath()
    }

    fillColor?.let {
      g.paint = it
      g.fill(path)
    }

    if (stacked) {
      // fix stroke cutting
      val area = Area(g.clip)
      area.subtract(Area(path))
      g.clip = area
    }
  }

  fun findLocation(xy: MinMax<X, Y>, coordinates: Coordinates<X, Y>): Point2D.Double = Point2D.Double(
    findX(xy, coordinates.x) + margins.left, findY(xy, coordinates.y) + margins.top
  )

  open fun add(x: X, y: Y) {
    datasets.firstOrNull()?.add(x to y)
  }

  fun getDataset(): D = datasets.first()

  @JvmName("getDataset")
  operator fun get(label: String): LineDataset<X, Y> = datasets.find { it.label == label } ?: throw NoSuchElementException("Cannot find dataset with label $datasets")

  fun clear() {
    datasets.forEach { (it.data as MutableList).clear() }
  }
}

open class CategoryLineChart<X: Number> : LineChart<Int, X, CategoryLineDataset<X>>()

open class XYLineChart<X: Number, Y: Number> : LineChart<X, Y, XYLineDataset<X, Y>>()

// HELPERS
/**
 * Calculates control points for bezier function and curves line.
 * Requires an array of 4 points in format (x0, y0, x1, y1, x2, y2, x3, y3),
 * where (x2, y2) — is target point, x1, y1 — point, that can be acquired by Path2D.currentPoint.
 * (x0, y0) — point before current and (x3, y3) —point after.
 *
 * @param neighbour array keeps 4 points (size 8)
 *
 * Using Monotone Cubic Splines: algorithm https://en.wikipedia.org/wiki/Monotone_cubic_interpolation
 */
private fun Path2D.Double.curveTo(neighbour: DoubleArray) {

  assert(neighbour.size == 8) { "Array must contain 4 points in format (x0, y0, x1, y1, x2, y2, x3, y3)" }

  val x0 = neighbour[0]
  val y0 = neighbour[1]
  val x1 = neighbour[2]
  val y1 = neighbour[3]
  val x2 = neighbour[4]
  val y2 = neighbour[5]
  val x3 = neighbour[6]
  val y3 = neighbour[7]

  val slope0 = ((y1 - y0) / (x1 - x0)).orZero()
  val slope1 = ((y2 - y1) / (x2 - x1)).orZero()
  val slope2 = ((y3 - y2) / (x3 - x2)).orZero()

  var tan1 = if (slope0 * slope1 <= 0) 0.0 else (slope0 + slope1) / 2
  var tan2 = if (slope1 * slope2 <= 0) 0.0 else (slope1 + slope2) / 2

  if (slope1 == 0.0) {
    tan1 = 0.0
    tan2 = 0.0
  } else {
    val a = tan1 / slope1
    val b = tan2 / slope1
    val h = hypot(a, b)
    if (h > 3.0) {
      val t = 3.0 / h
      tan1 = t * a * slope1
      tan2 = t * b * slope1
    }
  }

  val delta2 = (x2 - x1) / 3
  var cx0 = x1 + delta2
  var cy0 = y1 + delta2 * tan1

  if (x0.isNaN() || y0.isNaN()) {
    cx0 = x1
    cy0 = y1
  }

  val delta0 = (x2 - x1) / 3
  var cx1 = x2 - delta0
  var cy1 = y2 - delta0 * tan2

  if (x3.isNaN() || y3.isNaN()) {
    cx1 = x2
    cy1 = y2
  }

  curveTo(cx0, cy0, cx1, cy1, x2, y2)
}

private fun Double.orZero() = if (this.isNaN()) 0.0 else this

private fun DoubleArray.shiftLeftByTwo(first: Double, second: Double) {
  for (j in 2 until size) {
    this[j - 2] = this[j]
  }
  this[size - 2] = first
  this[size - 1] = second
}
