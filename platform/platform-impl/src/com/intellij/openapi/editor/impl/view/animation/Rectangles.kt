// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import java.awt.Rectangle
import java.awt.geom.Rectangle2D

internal val Rectangle2D.area: Double
  get() = width.coerceAtLeast(0.0) * height.coerceAtLeast(0.0)

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
