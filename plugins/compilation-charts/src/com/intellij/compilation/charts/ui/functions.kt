// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

import com.intellij.ui.ColorUtil
import com.intellij.util.JBHiDPIScaledImage
import java.awt.Color
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.hypot
import kotlin.math.roundToInt

internal fun Path2D.Double.curveTo(neighbour: DoubleArray) {

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
  }
  else {
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

internal fun BufferedImage.height(): Double = if (this is JBHiDPIScaledImage)
  height * 1 / scale
else
  height.toDouble()

private fun Double.orZero() = if (this.isNaN()) 0.0 else this

internal fun compareWithViewport(startTime: Long, finishTime: Long?, settings: ChartSettings, zoom: Zoom, viewport: Rectangle2D): Int {
  val x0 = viewport.x
  val x1 = x0 + viewport.width
  val startPixel = zoom.toPixels(startTime - settings.duration.from)
  if (startPixel > x1) return 1
  if (finishTime == null) return 0
  val finishPixel = zoom.toPixels(finishTime - settings.duration.from)
  if (finishPixel < x0) return -1
  return 0
}

fun Color.alpha(alpha: Double): Color = ColorUtil.withAlpha(this, alpha)

@Suppress("UseJBColor")
fun Color.alpha(background: Color, alpha: Double): Color {
  val red = (alpha * red + (1 - alpha) * background.red).roundToInt()
  val green = (alpha * green + (1 - alpha) * background.green).roundToInt()
  val blue = (alpha * blue + (1 - alpha) * background.blue).roundToInt()
  return Color(red, green, blue)
}