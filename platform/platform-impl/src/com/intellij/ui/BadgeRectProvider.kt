// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.jetbrains.annotations.ApiStatus
import java.awt.geom.RoundRectangle2D

@ApiStatus.Internal
@ApiStatus.Experimental
open class BadgeRectProvider : BadgeShapeProvider() {

  protected open fun getArc() = 0.5
  protected open fun getTop() = 0.0
  protected open fun getLeft() = 0.4
  protected open fun getRight() = 0.0
  protected open fun getBottom() = 0.7

  override fun createShape(width: Int, height: Int, hole: Boolean): RoundRectangle2D? {
    val size = width.coerceAtMost(height)
    if (size <= 0) return null

    val top = height * getTop()
    if (top >= height) return null

    val left = width * getLeft()
    if (left >= width) return null

    val right = width - width * getRight()
    if (right <= 0 || right <= left) return null

    val bottom = height - height * getBottom()
    if (bottom <= 0 || bottom <= top) return null

    val border = when {
      hole -> size * getBorder()
      else -> 0.0
    }
    val x = left - border
    val y = top - border
    val w = right + border - x
    val h = bottom + border - y
    val arc = w.coerceAtMost(h) * getArc().coerceIn(0.0, 1.0)
    return RoundRectangle2D.Double(x, y, w, h, arc, arc)
  }
}
