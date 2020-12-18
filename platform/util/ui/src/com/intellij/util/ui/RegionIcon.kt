// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.Objects
import kotlin.math.ceil

class RegionIcon(
  private val width: Int,
  private val height: Int,
  private val painter: RegionPainter<Component>
) : JBCachingScalableIcon<RegionIcon>() {

  constructor(size: Int, painter: RegionPainter<Component>) : this(size, size, painter)

  override fun copy(): RegionIcon {
    val icon = RegionIcon(width, height, painter)
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

  override fun hashCode(): Int = Objects.hash(width, height, painter)

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    val icon = other as? RegionIcon ?: return false
    return icon.width == width && icon.height == height && icon.painter == painter
  }
}
