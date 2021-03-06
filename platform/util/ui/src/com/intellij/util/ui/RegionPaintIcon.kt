// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.ui.scale.DerivedScaleType.PIX_SCALE
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.Objects
import kotlin.math.ceil

class RegionPaintIcon(
  private val width: Int,
  private val height: Int,
  private val top: Int,
  private val left: Int,
  private val bottom: Int,
  private val right: Int,
  private val painter: RegionPainter<Component?>
) : JBCachingScalableIcon<RegionPaintIcon>() {

  constructor(width: Int, height: Int, insets: Int, painter: RegionPainter<Component?>)
    : this(width, height, insets, insets, insets, insets, painter)

  constructor(width: Int, height: Int, painter: RegionPainter<Component?>)
    : this(width, height, 0, painter)

  constructor(size: Int, painter: RegionPainter<Component?>)
    : this(size, size, painter)

  override fun copy(): RegionPaintIcon {
    val icon = RegionPaintIcon(width, height, top, left, bottom, right, painter)
    icon.updateContextFrom(this)
    return icon
  }

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    if (g is Graphics2D) {
      val width = iconWidth
      val height = iconHeight
      val g2d = g.create(x, y, width, height) as Graphics2D
      try {
        val dx = scaled(left)
        val dy = scaled(top)
        val dw = dx + scaled(right)
        val dh = dy + scaled(bottom)
        painter.paint(g2d, dx, dy, width - dw, height - dh, c)
      }
      finally {
        g2d.dispose()
      }
    }
  }

  override fun getIconWidth() = scaled(width)

  override fun getIconHeight() = scaled(height)

  private fun scaled(size: Int) = if (size > 0) ceil(scaleVal(size.toDouble())).toInt() else 0

  override fun toString() = painter.toString()

  override fun hashCode(): Int = Objects.hash(width, height, painter)

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    val icon = other as? RegionPaintIcon ?: return false
    return icon.width == width &&
           icon.height == height &&
           icon.top == top &&
           icon.left == left &&
           icon.right == right &&
           icon.bottom == bottom &&
           icon.scaleVal(1.0, PIX_SCALE) == scaleVal(1.0, PIX_SCALE) &&
           icon.painter == painter
  }
}
