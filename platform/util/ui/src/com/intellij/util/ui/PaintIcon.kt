// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Paint
import javax.swing.border.Border
import kotlin.math.ceil

open class PaintIcon(private val width: Int, private val height: Int, private val painter: RegionPainter<Component?>)
  : JBCachingScalableIcon<PaintIcon>() {

  constructor(size: Int, painter: RegionPainter<Component?>) : this(size, size, painter)
  constructor(size: Int, paint: Paint) : this(size, size, paint)
  constructor(width: Int, height: Int, paint: Paint) : this(width, height, Painter(paint))
  constructor(icon: PaintIcon) : this(icon.width, icon.height, icon.painter) {
    isIconPreScaled = icon.isIconPreScaled
    border = icon.border
  }

  var border: Border? = null

  override fun copy() = PaintIcon(this)

  override fun equals(other: Any?) = other === this
  override fun hashCode() = System.identityHashCode(this)

  override fun getIconWidth() = ceil(scaleVal(width.toDouble())).toInt()
  override fun getIconHeight() = ceil(scaleVal(height.toDouble())).toInt()
  override fun paintIcon(component: Component, g: Graphics, x: Int, y: Int) {
    (g as? Graphics2D)?.let { painter.paint(it, x, y, iconWidth, iconHeight, component) }
    border?.paintBorder(component, g, x, y, iconWidth, iconHeight)
  }
}


private class Painter(private val paint: Paint) : RegionPainter<Component?> {
  override fun paint(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, component: Component?) {
    g.paint = paint
    g.fillRect(x, y, width, height)
  }
}
