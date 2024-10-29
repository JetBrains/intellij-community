// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChartUtils")
package com.intellij.ui.charts

import java.awt.Insets
import java.util.function.Consumer

// Common DSL

fun <X: Number, Y: Number, T: XYChartComponent<X, Y>> T.ranges(init: MinMax<X, Y>.() -> Unit) {
  init(this.ranges)
}

fun <X: Number, Y: Number, T: GridChartWrapper<X, Y>> T.grid(init: Grid<X, Y>.() -> Unit) {
  init(this.ranges)
}

fun ChartWrapper.margins(init: Insets.() -> Unit) {
  init(this.margins)
}

// Generator DSL

fun <T: Number> enumerator(vararg values: T) : ValueIterable<T> = object : ValueIterable<T>() {
  override fun iterator(): Iterator<T> = values.iterator()
}
fun generator(step: Int) : ValueIterable<Int> = object: ValueIterable<Int>() {
  override fun iterator(): Iterator<Int> = (min..max step step).iterator()
}
fun generator(step: Long) : ValueIterable<Long> = object: ValueIterable<Long>() {
  override fun iterator(): Iterator<Long> = (min..max step step).iterator()
}
fun generator(step: Float) : ValueIterable<Float> = object: ValueIterable<Float>() {
  override fun iterator(): Iterator<Float> = object: Iterator<Float> {
    var position: Int = 0
    override fun hasNext() = position * step + min <= max
    override fun next(): Float {
      val next = position * step + min
      position += 1
      return next
    }
  }
}

fun generator(step: Double) : ValueIterable<Double> = object: ValueIterable<Double>() {
  override fun iterator(): Iterator<Double> = object: Iterator<Double> {
    var position: Int = 0
    override fun hasNext() = position * step + min <= max
    override fun next(): Double {
      val next = position * step + min
      position += 1
      return next
    }
  }
}

// Grid

fun <X: Number, Y: Number> Grid<X, Y>.xPainter(converter: GridLine<X, Y, X>.() -> Unit) {
  xPainter = Consumer { converter(it) }
}
fun <X: Number, Y: Number> Grid<X, Y>.yPainter(converter: GridLine<X, Y, Y>.() -> Unit) {
  yPainter = Consumer { converter(it) }
}

fun <X: Number, Y: Number, T: Number> Grid<X, Y>.format(str: String): Consumer<GridLine<X, Y, T>> = Consumer<GridLine<X, Y, T>> {
  it.label = str.format(it.value)
}

/* XY Line DSL */

class XYDataHolder<X: Number, Y: Number>(val data: MutableList<XYLineDataset<X, Y>>)

class XYDataCreator<X: Number, Y: Number> {
  lateinit var x: Iterable<X>
  lateinit var y: Iterable<Y>
}

class XYDataGenerator<X: Number, Y: Number> {
  lateinit var x: Iterable<X>
  lateinit var y: (x: X) -> Y
}

fun <X: Number, Y: Number> lineChart(ink: XYLineChart<X, Y>.() -> Unit): XYLineChart<X, Y> = XYLineChart<X, Y>().apply(ink)

fun <X: Number, Y: Number> XYLineChart<X, Y>.datasets(body: XYDataHolder<X, Y>.() -> Unit) {
  datasets = XYDataHolder(mutableListOf<XYLineDataset<X, Y>>()).apply(body).data
}

fun <X: Number, Y: Number> XYDataHolder<X, Y>.dataset(body: XYLineDataset<X, Y>.() -> Unit) {
  data.add(XYLineDataset<X, Y>().apply(body))
}
fun <X: Number, Y: Number> XYLineChart<X, Y>.dataset(body: XYLineDataset<X, Y>.() -> Unit) {
  datasets = mutableListOf(XYLineDataset<X, Y>().apply(body))
}

fun <X: Number, Y: Number> XYLineDataset<X, Y>.values(creator: XYDataCreator<X, Y>.() -> Unit) {
  val values = XYDataCreator<X, Y>().apply(creator)
  this.data = object: Iterable<Coordinates<X, Y>> {
    override fun iterator(): Iterator<Coordinates<X, Y>> = object: Iterator<Coordinates<X, Y>> {
      val xIter = values.x.iterator()
      val yIter = values.y.iterator()
      override fun hasNext() = xIter.hasNext() && yIter.hasNext()
      override fun next() = xIter.next() to yIter.next()
    }
  }
}

fun <X: Number, Y: Number> XYLineDataset<X, Y>.generate(generator: XYDataGenerator<X, Y>.() -> Unit) {
  val iterator = XYDataGenerator<X, Y>().apply(generator)
  this.data = object: Iterable<Coordinates<X, Y>> {
    override fun iterator(): Iterator<Coordinates<X, Y>> = object: Iterator<Coordinates<X, Y>> {
      val i = iterator.x.iterator()
      override fun hasNext() = i.hasNext()
      override fun next(): Coordinates<X, Y> = i.next().let { it to iterator.y(it) }
    }
  }
}

/* Category Line DSL */

class CategoryDataHolder<X: Number>(val data: MutableList<CategoryLineDataset<X>>)

fun <X: Number> lineChart(ink: CategoryLineChart<X>.() -> Unit): CategoryLineChart<X> = CategoryLineChart<X>().apply(ink)

fun <X: Number> CategoryLineChart<X>.datasets(body: CategoryDataHolder<X>.() -> Unit) {
  datasets = CategoryDataHolder(mutableListOf<CategoryLineDataset<X>>()).apply(body).data
}

fun <X: Number> CategoryDataHolder<X>.dataset(body: CategoryLineDataset<X>.() -> Unit) {
  data.add(CategoryLineDataset<X>().apply(body))
}

fun <X: Number> CategoryLineChart<X>.dataset(body: CategoryLineDataset<X>.() -> Unit) {
  datasets = mutableListOf(CategoryLineDataset<X>().apply(body))
}

/* Bar Chart DSL */

class BarDataGenerator<R: Number> {
  var count: Int = -1
    set(value) {
      if (value < 0) throw IllegalArgumentException("Value cannot be less than 0")
      field = value
    }
  lateinit var function: (x: Int) -> R
}

class BarDataHolder<T: Number>(val data: MutableList<BarDataset<T>>)

fun <T: Number> barChart(ink: HorizontalBarChart<T>.() -> Unit): HorizontalBarChart<T> = HorizontalBarChart<T>().apply(ink)

fun <T: Number> HorizontalBarChart<T>.datasets(body: BarDataHolder<T>.() -> Unit) {
  datasets = BarDataHolder(mutableListOf<BarDataset<T>>()).apply(body).data
}

fun <T: Number> HorizontalBarChart<T>.dataset(body: BarDataset<T>.() -> Unit) {
  datasets = listOf(BarDataset<T>().apply(body))
}

fun <T: Number> BarDataHolder<T>.dataset(body: BarDataset<T>.() -> Unit) {
  data.add(BarDataset<T>().apply(body))
}

fun <R: Number> generate(generator: BarDataGenerator<R>.() -> Unit): Iterable<R> {
  val iterator = BarDataGenerator<R>().apply(generator)
  return object : Iterable<R> {
    override fun iterator(): Iterator<R> = object: Iterator<R> {
      var index = -1
      override fun hasNext() = index < iterator.count
      override fun next(): R = iterator.function(++index)
    }
  }
}

// Timeline helpers

val times: Array<Int> = arrayOf(
  1, 2, 5, 10, 25, 50, 100, 200, 500, // milliseconds
  1_000, 2_000, 5_000, 15_000, 30_000, // seconds
  60_000, 120_000, 300_000, 600_000, 900_000, 1_800_000, // minutes
  3_600_000, 7_200_000, 14_400_000 // hours
)

fun ChartWrapper.findScale(xMin: Long, xMax: Long, spaceWidth: Int = 100): Int {
  val duration = xMax - xMin
  val width = width.toDouble()
  val lineCount = width / spaceWidth
  var scale = times[0]
  for (i in times.indices) {
    scale = times[i]
    if (duration / scale <= lineCount) {
      break
    }
  }
  return scale
}