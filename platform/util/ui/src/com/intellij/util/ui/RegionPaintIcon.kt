// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  private val painter: RegionPainter<Component?>
) : JBCachingScalableIcon<RegionPaintIcon>() {

  constructor(size: Int, painter: RegionPainter<Component?>) : this(size, size, painter)

  override fun copy(): RegionPaintIcon {
    val icon = RegionPaintIcon(width, height, painter)
    icon.updateContextFrom(this)
    return icon
  }

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    if (g is Graphics2D) {
      val width = iconWidth
      val height = iconHeight
      val g2d = g.create(x, y, width, height) as Graphics2D
      try {
        painter.paint(g2d, 0, 0, width, height, c)
      }
      finally {
        g2d.dispose()
      }
    }
  }

  override fun getIconWidth() = ceil(scaleVal(width.toDouble())).toInt()

  override fun getIconHeight() = ceil(scaleVal(height.toDouble())).toInt()

  private fun getPixWidth() = scaleVal(width.toDouble(), PIX_SCALE)

  private fun getPixHeight() = scaleVal(height.toDouble(), PIX_SCALE)

  override fun toString() = painter.toString()

  override fun hashCode(): Int = Objects.hash(width, height, painter)

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    val icon = other as? RegionPaintIcon ?: return false
    return icon.getPixWidth() == getPixWidth() &&
           icon.getPixHeight() == getPixHeight() &&
           icon.painter == painter
  }
}
