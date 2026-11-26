// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D

/**
 * Creates the shape of a drop placeholder that covers [area].
 *
 * The shape is the part of [area] that is in [cornerArea] with its corners rounded by [arc].
 * So a corner of the shape is rounded only where [area] reaches a corner of [cornerArea].
 * The curve is the curve of [cornerArea] for each size of [area]. [cornerArea] must not include a border around [area].
 *
 * An [area] out of [cornerArea] gives an empty shape. An [area] away from the rounded corners gives the rectangle itself.
 */
@ApiStatus.Internal
fun createDropAreaShape(area: Rectangle, cornerArea: Rectangle, arc: Int): Shape {
  val visibleArea = area.intersection(cornerArea)
  if (visibleArea.isEmpty) return Rectangle()

  val diameter = minOf(arc, cornerArea.width, cornerArea.height).toDouble()
  if (diameter <= 0) return visibleArea

  val roundedArea = RoundRectangle2D.Double(
    cornerArea.x.toDouble(),
    cornerArea.y.toDouble(),
    cornerArea.width.toDouble(),
    cornerArea.height.toDouble(),
    diameter,
    diameter,
  )

  val result = Area(roundedArea)
  result.intersect(Area(visibleArea))

  if (result.isEmpty) return Rectangle()
  if (result.isRectangular && result.bounds2D == visibleArea) return visibleArea

  return result
}
