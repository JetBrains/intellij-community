// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

/**
 * Creates the shape of a drop placeholder that covers [area].
 *
 * A corner of the shape is rounded with [arc] only at a corner of [cornerArea]. The other corners stay square.
 * A side of [area] is at a side of [cornerArea] when the gap between them is less than [arc].
 * So the insets of [cornerArea] do not make a corner square.
 */
@ApiStatus.Internal
fun createDropAreaShape(area: Rectangle, cornerArea: Rectangle, arc: Int): Shape {
  val atLeft = area.x - cornerArea.x < arc
  val atTop = area.y - cornerArea.y < arc
  val atRight = cornerArea.maxX - area.maxX < arc
  val atBottom = cornerArea.maxY - area.maxY < arc

  val x = area.x.toDouble()
  val y = area.y.toDouble()
  val width = area.width.toDouble()
  val height = area.height.toDouble()
  val radius = arc / 2.0

  val shape = Area(RoundRectangle2D.Double(x, y, width, height, arc.toDouble(), arc.toDouble()))
  fun fillCorner(cornerX: Double, cornerY: Double) {
    shape.add(Area(Rectangle2D.Double(cornerX, cornerY, radius, radius)))
  }
  if (!(atLeft && atTop)) fillCorner(x, y)
  if (!(atRight && atTop)) fillCorner(x + width - radius, y)
  if (!(atLeft && atBottom)) fillCorner(x, y + height - radius)
  if (!(atRight && atBottom)) fillCorner(x + width - radius, y + height - radius)
  return shape
}
