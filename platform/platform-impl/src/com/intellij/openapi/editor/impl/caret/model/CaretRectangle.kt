// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import com.intellij.openapi.editor.Caret
import java.awt.geom.Point2D
import kotlin.math.max

internal const val CARET_REPAINT_RECTANGLE_MARGIN = 1
internal const val CARET_CACHE_RECTANGLE_MARGIN = CARET_REPAINT_RECTANGLE_MARGIN + 1

private const val MIN_WIDTH = 2f

internal class CaretRectangle private constructor(
  val x: Double,
  val y: Double,
  rawWidth: Float,
  val caret: Caret?,
  val isRtl: Boolean,
) {
  val width: Float = max(rawWidth, MIN_WIDTH)

  override fun toString(): String = "CaretRectangle(x=$x, y=$y, width=$width, caret=$caret, isRtl=$isRtl)"

  companion object {
    @JvmField
    val PLACEHOLDER: CaretRectangle = CaretRectangle(0.0, 0.0, 0f, null, false)

    internal fun at(position: Point2D, width: Float, caret: Caret?, isRtl: Boolean): CaretRectangle =
      CaretRectangle(position.x, position.y, width, caret, isRtl)
  }
}
