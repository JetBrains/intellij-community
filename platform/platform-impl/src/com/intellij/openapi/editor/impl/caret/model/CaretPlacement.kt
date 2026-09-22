// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.LogicalPosition
import java.awt.geom.Point2D
import kotlin.math.hypot

internal data class CaretPlacement(
  private val caret: Caret,
  private val x: Double,
  private val y: Double,
  private val logicalPosition: LogicalPosition,
  private val visualColumnAdjustment: Int,
  private val isAtBoundary: Boolean,
  private val width: Float,
  private val isRtl: Boolean,
) {
  fun toPoint(): Point2D.Double {
    return Point2D.Double(x, y)
  }

  fun caret(): Caret {
    return caret
  }

  fun x(): Double {
    return x
  }

  fun y(): Double {
    return y
  }

  /**
   * How far [point] still is from where this caret belongs.
   */
  fun distanceTo(point: Point2D): Double {
    val dx = x - point.x
    val dy = y - point.y
    return hypot(dx, dy)
  }

  /**
   * Whether [other] lands on the same pixel, no matter which document position it came from.
   */
  fun isVisuallyAt(other: CaretPlacement): Boolean {
    val distance = Point2D.distance(x, y, other.x, other.y)
    return distance <= VISUAL_EPSILON
  }

  /**
   * Whether [other] denotes the same document position, no matter where that position is painted.
   */
  fun isSamePlace(other: CaretPlacement): Boolean {
    val samePosition = logicalPosition == other.logicalPosition
    val sameAdjustment = visualColumnAdjustment == other.visualColumnAdjustment
    val sameBoundary = isAtBoundary == other.isAtBoundary
    return samePosition && sameAdjustment && sameBoundary
  }

  /**
   * Whether [other] is the same caret in the same place with the same appearance.
   */
  fun matches(other: CaretPlacement): Boolean {
    val sameCaret = caret == other.caret
    val samePoint = x == other.x && y == other.y
    val sameShape = width == other.width && isRtl == other.isRtl
    return sameCaret && samePoint && sameShape && isSamePlace(other)
  }

  fun rectangleAt(position: Point2D): CaretRectangle {
    return CaretRectangle.at(position, width, caret, isRtl)
  }

  companion object {
    /**
     * Two placements this close together paint on the same pixel, so the caret need not move at all.
     */
    private const val VISUAL_EPSILON = 0.1
  }
}
