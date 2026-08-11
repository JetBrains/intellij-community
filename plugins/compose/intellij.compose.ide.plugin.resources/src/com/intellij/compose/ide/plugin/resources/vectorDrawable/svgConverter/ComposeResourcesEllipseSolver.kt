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
package com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter

import com.intellij.openapi.diagnostic.logger
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Based on [com.android.ide.common.vectordrawable.EllipseSolver]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/df6a294cc4b3e010d3e64177c95888726520d3fe:sdk-common/src/main/java/com/android/ide/common/vectordrawable/EllipseSolver.java
 */
internal class ComposeResourcesEllipseSolver(
  totalTransform: AffineTransform,
  currentX: Float, currentY: Float,
  rx: Float, ry: Float, rotation: Float,
  largeArcFlag: Float, sweepFlag: Float,
  endX: Float, endY: Float,
) {
  var majorAxis = 0f
    private set
  var minorAxis = 0f
    private set
  var rotationDegree = 0f
    private set
  var directionChanged = false
    private set

  init {
    computeTransformedEllipse(
      totalTransform, currentX, currentY,
      rx, ry, rotation,
      largeArcFlag != 0f, sweepFlag != 0f,
      endX, endY
    )
  }

  private fun computeTransformedEllipse(
    totalTransform: AffineTransform,
    currentX: Float, currentY: Float,
    rx: Float, ry: Float, rotation: Float,
    largeArc: Boolean, sweep: Boolean,
    endX: Float, endY: Float,
  ) {
    if (rx == 0f || ry == 0f) return

    val center = computeOriginalCenter(currentX, currentY, rx, ry, rotation, largeArc, sweep, endX, endY)
    val rotationRadians = rotation.toDouble()

    val majorAxisPoint = Point2D.Double(rx.toDouble(), 0.0).rotateAndTranslate(rotationRadians, center)
    val minorAxisPoint = Point2D.Double(0.0, ry.toDouble()).rotateAndTranslate(rotationRadians, center)

    val middleRadians = Math.PI / 4
    val middleR = rx * ry / hypot(ry * cos(middleRadians), rx * sin(middleRadians))
    val middlePoint = Point2D.Double(middleR * cos(middleRadians), middleR * sin(middleRadians))
      .rotateAndTranslate(rotationRadians, center)

    val dstMiddlePoint = totalTransform.transform(middlePoint, null)
    val dstMajorAxisPoint = totalTransform.transform(majorAxisPoint, null)
    val dstMinorAxisPoint = totalTransform.transform(minorAxisPoint, null)
    val dstCenter = totalTransform.transform(center, null)

    val relMiddle = dstMiddlePoint.relativeTo(dstCenter)
    val relMajor = dstMajorAxisPoint.relativeTo(dstCenter)
    val relMinor = dstMinorAxisPoint.relativeTo(dstCenter)

    directionChanged = computeDirectionChange(
      middlePoint, majorAxisPoint, minorAxisPoint,
      dstMiddlePoint, dstMajorAxisPoint, dstMinorAxisPoint
    )

    if (!computeABThetaFromControlPoints(relMiddle, relMajor, relMinor)) return

    LOG.warn("Early return in the ellipse transformation computation!")
  }

  /**
   * Returns `true` if there is an error.
   * This error is ignorable, but the output ellipse will not be correct.
   */
  private fun computeABThetaFromControlPoints(middle: Point2D, major: Point2D, minor: Point2D): Boolean {
    val m11 = middle.x * middle.x
    val m12 = middle.x * middle.y
    val m13 = middle.y * middle.y

    val m21 = major.x * major.x
    val m22 = major.x * major.y
    val m23 = major.y * major.y

    val m31 = minor.x * minor.x
    val m32 = minor.x * minor.y
    val m33 = minor.y * minor.y

    val det = -(m13 * m22 * m31 - m12 * m23 * m31 - m13 * m21 * m32 + m11 * m23 * m32 + m12 * m21 * m33 - m11 * m22 * m33)
    if (det == 0.0) return true

    val a = (-m13 * m22 + m12 * m23 + m13 * m32 - m23 * m32 - m12 * m33 + m22 * m33) / det
    val b = (m13 * m21 - m11 * m23 - m13 * m31 + m23 * m31 + m11 * m33 - m21 * m33) / det
    val c = (m12 * m21 - m11 * m22 - m12 * m31 + m22 * m31 + m11 * m32 - m21 * m32) / (-det)

    if (a - c == 0.0) {
      minorAxis = hypot(major.x, major.y).toFloat()
      majorAxis = minorAxis
      rotationDegree = 0f
      return false
    }

    val doubleThetaInRadians = atan(b / (a - c))

    if (sin(doubleThetaInRadians) == 0.0) {
      minorAxis = sqrt(1 / c).toFloat()
      majorAxis = sqrt(1 / a).toFloat()
      rotationDegree = 0f
      return false
    }

    val bSqInv = (a + c + b / sin(doubleThetaInRadians)) / 2
    val aSqInv = a + c - bSqInv

    if (bSqInv == 0.0 || aSqInv == 0.0) return true

    val thetaInRadians = doubleThetaInRadians / 2
    minorAxis = sqrt(1 / bSqInv).toFloat()
    majorAxis = sqrt(1 / aSqInv).toFloat()
    rotationDegree = Math.toDegrees(Math.PI / 2 + thetaInRadians).toFloat()
    return false
  }

  private fun Point2D.rotate(radians: Double): Point2D.Double {
    val cos = cos(radians)
    val sin = sin(radians)
    return Point2D.Double(x * cos - y * sin, x * sin + y * cos)
  }

  private fun Point2D.rotateAndTranslate(radians: Double, center: Point2D): Point2D.Double {
    val rotated = rotate(radians)
    return Point2D.Double(rotated.x + center.x, rotated.y + center.y)
  }

  private fun Point2D.relativeTo(center: Point2D) = Point2D.Double(x - center.x, y - center.y)

  private fun computeDirectionChange(
    middlePoint: Point2D, majorAxisPoint: Point2D, minorAxisPoint: Point2D,
    dstMiddlePoint: Point2D, dstMajorAxisPoint: Point2D, dstMinorAxisPoint: Point2D,
  ): Boolean {
    val srcCrossProduct = getCrossProduct(middlePoint, majorAxisPoint, minorAxisPoint)
    val dstCrossProduct = getCrossProduct(dstMiddlePoint, dstMajorAxisPoint, dstMinorAxisPoint)
    return srcCrossProduct * dstCrossProduct < 0
  }

  private fun getCrossProduct(middlePoint: Point2D, majorAxisPoint: Point2D, minorAxisPoint: Point2D): Double {
    val majorMinusMiddleX = majorAxisPoint.x - middlePoint.x
    val majorMinusMiddleY = majorAxisPoint.y - middlePoint.y
    val minorMinusMiddleX = minorAxisPoint.x - middlePoint.x
    val minorMinusMiddleY = minorAxisPoint.y - middlePoint.y
    return majorMinusMiddleX * minorMinusMiddleY - majorMinusMiddleY * minorMinusMiddleX
  }

  private fun computeOriginalCenter(
    x1: Float, y1: Float, rx: Float, ry: Float, phi: Float,
    largeArc: Boolean, sweep: Boolean, x2: Float, y2: Float,
  ): Point2D.Double {
    val cosPhi = cos(phi.toDouble())
    val sinPhi = sin(phi.toDouble())
    val xDelta = ((x1 - x2) / 2).toDouble()
    val yDelta = ((y1 - y2) / 2).toDouble()

    val tempX1 = cosPhi * xDelta + sinPhi * yDelta
    val tempY1 = -sinPhi * xDelta + cosPhi * yDelta

    val rxSq = (rx * rx).toDouble()
    val rySq = (ry * ry).toDouble()
    val tempX1Sq = tempX1 * tempX1
    val tempY1Sq = tempY1 * tempY1

    val baseFactor = (rxSq * rySq - rxSq * tempY1Sq - rySq * tempX1Sq) / (rxSq * tempY1Sq + rySq * tempX1Sq)
    val sign = if (largeArc == sweep) -1.0 else 1.0
    val tempCenterFactor = sqrt(baseFactor.coerceAtLeast(0.0)) * sign

    val tempCx = tempCenterFactor * rx * tempY1 / ry
    val tempCy = -tempCenterFactor * ry * tempX1 / rx

    val xCenter = ((x1 + x2) / 2).toDouble()
    val yCenter = ((y1 + y2) / 2).toDouble()

    return Point2D.Double(
      cosPhi * tempCx - sinPhi * tempCy + xCenter,
      sinPhi * tempCx + cosPhi * tempCy + yCenter
    )
  }

  companion object {
    private val LOG = logger<ComposeResourcesEllipseSolver>()
  }
}
