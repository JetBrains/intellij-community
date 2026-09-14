// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.geom.Rectangle2D

/**
 * The border of [this] rectangle, one pixel thick, as a shape that can be filled and can shape a window.
 *
 * A rectangle too small to hold a border of its own becomes a solid shape instead.
 */
internal fun Rectangle2D.borderRing(): Area {
  val ring = Area(this)
  val interior = Rectangle2D.Double(
    x + BORDER_THICKNESS,
    y + BORDER_THICKNESS,
    width - 2 * BORDER_THICKNESS,
    height - 2 * BORDER_THICKNESS,
  )
  ring.subtract(Area(interior))
  return ring
}

internal fun Rectangle2D.area(): Double {
  return width.coerceAtLeast(0.0) * height.coerceAtLeast(0.0)
}

internal fun Rectangle2D.coerceAtLeastEmpty(): Rectangle2D {
  val newX = x.coerceAtLeast(0.0)
  val newY = y.coerceAtLeast(0.0)
  val newWidth = (width + (x - newX)).coerceAtLeast(0.0)
  val newHeight = (height + (y - newY)).coerceAtLeast(0.0)
  return Rectangle2D.Double(newX, newY, newWidth, newHeight)
}

internal fun Rectangle.coerceAtLeastEmpty(): Rectangle {
  val newX = x.coerceAtLeast(0)
  val newY = y.coerceAtLeast(0)
  val newWidth = (width + (x - newX)).coerceAtLeast(0)
  val newHeight = (height + (y - newY)).coerceAtLeast(0)
  return Rectangle(newX, newY, newWidth, newHeight)
}

internal fun Rectangle2D.intersectWithVisibleArea(visibleArea: Rectangle): Rectangle2D? {
  if (!intersects(visibleArea)) {
    return null
  }
  return createIntersection(visibleArea)
}

private const val BORDER_THICKNESS = 1.0
