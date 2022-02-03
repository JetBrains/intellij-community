// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.jetbrains.annotations.ApiStatus
import java.awt.geom.Ellipse2D

@ApiStatus.Internal
@ApiStatus.Experimental
open class BadgeDotProvider : BadgeShapeProvider() {

  /** @return X-position of the badge relative to the icon width [0..1] */
  protected open fun getX() = getDouble("IconBadge.dotX", 16.5) / 20

  /** @return Y-position of the badge relative to the icon height [0..1] */
  protected open fun getY() = getDouble("IconBadge.dotY", 3.5) / 20

  /** @return radius of the badge relative to the icon size [0..1] */
  protected open fun getRadius() = getDouble("IconBadge.dotRadius", 3.5) / 20

  override fun createShape(width: Int, height: Int, hole: Boolean): Ellipse2D? {
    val size = width.coerceAtMost(height)
    if (size <= 0) return null

    val radius = size * getRadius()
    if (radius <= 0) return null

    val x = width * getX()
    if (0 > x + radius || x - radius > width) return null

    val y = height * getY()
    if (0 > y + radius || y - radius > height) return null

    val border = when {
      hole -> size * getBorder()
      else -> 0.0
    }
    val r = radius + border.coerceAtLeast(0.0)
    return Ellipse2D.Double(x - r, y - r, r + r, r + r)
  }
}
