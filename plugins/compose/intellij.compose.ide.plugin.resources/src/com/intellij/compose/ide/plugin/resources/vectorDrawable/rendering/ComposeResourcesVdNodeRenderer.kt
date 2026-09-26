/*
 * Copyright (C) 2015 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering

import java.awt.geom.Path2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Based on [com.android.ide.common.vectordrawable.VdNodeRender]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/949ad41a9f955d453649758270897e9df078ba17:sdk-common/src/main/java/com/android/ide/common/vectordrawable/VdNodeRender.java
 *
 * Key differences from Android original:
 * - Converted from Java class with static methods to Kotlin top-level functions
 * - Uses [PathState] class instead of separate local variables scattered across methods
 */
internal fun applyNodesToPath(nodes: Array<ComposeResourcesVdPath.Node>, path: Path2D) {
  val state = PathState()
  var lastCmd = ' '

  for (node in nodes) {
    processCommandSequence(path, state, node.type, lastCmd, node.params)
    lastCmd = node.type
  }
}

private fun processCommandSequence(path: Path2D, state: PathState, cmd: Char, previousCmd: Char, params: FloatArray) {
  if (cmd.lowercaseChar() == 'z') {
    path.closePath()
    state.cx = state.loopX
    state.cy = state.loopY
    return
  }

  val incr = when (cmd.lowercaseChar()) {
    'a' -> 7
    'c' -> 6
    'q', 's' -> 4
    'h', 'v' -> 1
    else -> 2 // m, l, t
  }
  var k = 0
  var currentCmd = previousCmd

  while (k < params.size) {
    processSegment(path, state, cmd, currentCmd, params, k)
    currentCmd = cmd
    k += incr
  }
}

private fun processSegment(p: Path2D, s: PathState, cmd: Char, currentCmd: Char, v: FloatArray, k: Int) {
  val ox = if (cmd.isLowerCase()) s.cx else 0.0
  val oy = if (cmd.isLowerCase()) s.cy else 0.0

  when (cmd.lowercaseChar()) {
    'm' -> {
      s.cx = ox + v.getDouble(k)
      s.cy = oy + v.getDouble(k + 1)

      if (k > 0) {
        p.lineTo(s.cx, s.cy)
      }
      else {
        p.moveTo(s.cx, s.cy)
        s.loopX = s.cx
        s.loopY = s.cy
      }
    }
    'l' -> {
      s.cx = ox + v.getDouble(k)
      s.cy = oy + v.getDouble(k + 1)
      p.lineTo(s.cx, s.cy)
    }
    'h' -> {
      s.cx = ox + v.getDouble(k)
      p.lineTo(s.cx, s.cy)
    }
    'v' -> {
      s.cy = oy + v.getDouble(k)
      p.lineTo(s.cx, s.cy)
    }
    'c' -> {
      p.curveTo(ox + v.getDouble(k),
                oy + v.getDouble(k + 1),
                ox + v.getDouble(k + 2),
                oy + v.getDouble(k + 3),
                ox + v.getDouble(k + 4),
                oy + v.getDouble(k + 5))
      s.cpx = ox + v.getDouble(k + 2)
      s.cpy = oy + v.getDouble(k + 3)
      s.cx = ox + v.getDouble(k + 4)
      s.cy = oy + v.getDouble(k + 5)
    }
    's' -> {
      val ctrl1X = s.reflectX(currentCmd, "cCsS")
      val ctrl1Y = s.reflectY(currentCmd, "cCsS")

      val ctrl2X = ox + v.getDouble(k)
      val ctrl2Y = oy + v.getDouble(k + 1)

      val endX = ox + v.getDouble(k + 2)
      val endY = oy + v.getDouble(k + 3)

      p.curveTo(ctrl1X, ctrl1Y, ctrl2X, ctrl2Y, endX, endY)
      s.update(endX, endY, ctrl2X, ctrl2Y)
    }
    'q' -> {
      p.quadTo(ox + v.getDouble(k), oy + v.getDouble(k + 1), ox + v.getDouble(k + 2), oy + v.getDouble(k + 3))
      s.cpx = ox + v.getDouble(k)
      s.cpy = oy + v.getDouble(k + 1)
      s.cx = ox + v.getDouble(k + 2)
      s.cy = oy + v.getDouble(k + 3)
    }
    't' -> {
      val ctrlX = s.reflectX(currentCmd, "qQtT")
      val ctrlY = s.reflectY(currentCmd, "qQtT")

      val endX = ox + v.getDouble(k)
      val endY = oy + v.getDouble(k + 1)

      p.quadTo(ctrlX, ctrlY, endX, endY)
      s.update(endX, endY, ctrlX, ctrlY)
    }
    'a' -> {
      val endX = v.getDouble(k + 5) + ox
      val endY = v.getDouble(k + 6) + oy

      drawArc(p, s.cx, s.cy, endX, endY, abs(v.getDouble(k)), abs(v.getDouble(k + 1)), v.getDouble(k + 2), v[k + 3] != 0f, v[k + 4] != 0f)

      s.cx = endX
      s.cy = endY
      s.cpx = endX
      s.cpy = endY
    }
  }
}

private fun drawArc(
  p: Path2D,
  x0: Double, y0: Double,
  x1: Double, y1: Double,
  a: Double, b: Double,
  theta: Double,
  isMoreThanHalf: Boolean, isPositiveArc: Boolean,
) {
  val thetaD = Math.toRadians(theta)
  val cosTheta = cos(thetaD)
  val sinTheta = sin(thetaD)

  val x0p = (x0 * cosTheta + y0 * sinTheta) / a
  val y0p = (-x0 * sinTheta + y0 * cosTheta) / b
  val x1p = (x1 * cosTheta + y1 * sinTheta) / a
  val y1p = (-x1 * sinTheta + y1 * cosTheta) / b

  val dx = x0p - x1p
  val dy = y0p - y1p
  val xm = (x0p + x1p) / 2.0
  val ym = (y0p + y1p) / 2.0
  val dsq = dx * dx + dy * dy

  if (dsq == 0.0) return

  val disc = 1.0 / dsq - 0.25
  if (disc < 0.0) {
    val adjust = sqrt(dsq) / 1.99999
    drawArc(p, x0, y0, x1, y1, a * adjust, b * adjust, theta, isMoreThanHalf, isPositiveArc)
    return
  }

  val s = sqrt(disc)
  val cx = if (isMoreThanHalf == isPositiveArc) xm - s * dy else xm + s * dy
  val cy = if (isMoreThanHalf == isPositiveArc) ym + s * dx else ym - s * dx

  val eta0 = atan2(y0p - cy, x0p - cx)
  val eta1 = atan2(y1p - cy, x1p - cx)
  var sweep = eta1 - eta0

  if (isPositiveArc != sweep >= 0.0) {
    sweep += if (sweep > 0.0) -(2.0 * PI) else (2.0 * PI)
  }

  val mappedCx = cx * a
  val mappedCy = cy * b
  val finalCx = mappedCx * cosTheta - mappedCy * sinTheta
  val finalCy = mappedCx * sinTheta + mappedCy * cosTheta

  arcToBezier(p, finalCx, finalCy, a, b, x0, y0, thetaD, eta0, sweep)
}

private fun arcToBezier(
  p: Path2D,
  cx: Double, cy: Double,
  a: Double, b: Double,
  startX: Double, startY: Double,
  theta: Double, start: Double, sweep: Double,
) {
  var e1x = startX
  var e1y = startY
  var eta1 = start

  val numSegments = ceil(abs(sweep * 4.0 / PI)).toInt()
  val cosTheta = cos(theta)
  val sinTheta = sin(theta)

  var ep1x = -a * cosTheta * sin(start) - b * sinTheta * cos(start)
  var ep1y = -a * sinTheta * sin(start) + b * cosTheta * cos(start)
  val anglePerSegment = sweep / numSegments.toDouble()

  repeat(numSegments) {
    val eta2 = eta1 + anglePerSegment
    val sinEta2 = sin(eta2)
    val cosEta2 = cos(eta2)

    val e2x = cx + a * cosTheta * cosEta2 - b * sinTheta * sinEta2
    val e2y = cy + a * sinTheta * cosEta2 + b * cosTheta * sinEta2
    val ep2x = -a * cosTheta * sinEta2 - b * sinTheta * cosEta2
    val ep2y = -a * sinTheta * sinEta2 + b * cosTheta * cosEta2

    val tanDiff2 = tan((eta2 - eta1) / 2.0)
    val alpha = sin(eta2 - eta1) * (sqrt(4.0 + 3.0 * tanDiff2 * tanDiff2) - 1.0) / 3.0

    p.curveTo(e1x + alpha * ep1x, e1y + alpha * ep1y, e2x - alpha * ep2x, e2y - alpha * ep2y, e2x, e2y)

    eta1 = eta2
    e1x = e2x
    e1y = e2y
    ep1x = ep2x
    ep1y = ep2y
  }
}

private class PathState {
  var cx = 0.0
  var cy = 0.0
  var cpx = 0.0
  var cpy = 0.0
  var loopX = 0.0
  var loopY = 0.0

  fun update(newCx: Double, newCy: Double, newCpx: Double = newCx, newCpy: Double = newCy) {
    cx = newCx
    cy = newCy
    cpx = newCpx
    cpy = newCpy
  }

  fun reflectX(lastCmd: Char, validCmd: String): Double =
    if (lastCmd in validCmd) 2.0 * cx - cpx else cx

  fun reflectY(lastCmd: Char, validCmd: String): Double =
    if (lastCmd in validCmd) 2.0 * cy - cpy else cy
}

private fun FloatArray.getDouble(index: Int): Double = this[index].toDouble()