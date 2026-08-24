// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.LogicalPosition
import java.awt.geom.Point2D

private const val VISUAL_EPSILON = 0.1

internal data class CaretPlacement(
  internal val caret: Caret,
  internal val x: Double,
  internal val y: Double,
  private val logicalPosition: LogicalPosition,
  private val visualColumnAdjustment: Int,
  private val isAtBoundary: Boolean,
  private val width: Float,
  private val isRtl: Boolean,
) {
  internal fun toPoint(): Point2D.Double = Point2D.Double(x, y)

  internal fun isVisuallyAt(other: CaretPlacement): Boolean = Point2D.distance(x, y, other.x, other.y) <= VISUAL_EPSILON

  internal fun isSamePlace(other: CaretPlacement): Boolean =
    logicalPosition == other.logicalPosition &&
    visualColumnAdjustment == other.visualColumnAdjustment &&
    isAtBoundary == other.isAtBoundary

  internal fun matches(other: CaretPlacement): Boolean =
    caret == other.caret &&
    x == other.x &&
    y == other.y &&
    width == other.width &&
    isRtl == other.isRtl &&
    isSamePlace(other)

  internal fun rectangleAt(position: Point2D): CaretRectangle = CaretRectangle.at(position, width, caret, isRtl)
}
