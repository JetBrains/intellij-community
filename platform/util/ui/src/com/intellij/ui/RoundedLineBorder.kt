// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.RectanglePainter2D
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.border.LineBorder

@ApiStatus.NonExtendable
open class RoundedLineBorder @JvmOverloads constructor(
  color: Color?,
  private val arcDiameter: Int = 1,
  thickness: Int = 1
) : LineBorder(color, thickness) {
  fun setColor(color: Color) {
    lineColor = color
  }

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2d = g as? Graphics2D ?: return
    g2d.color = lineColor
    RectanglePainter2D.DRAW.paint(g2d,
                                  x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble(),
                                  arcDiameter.toDouble(), LinePainter2D.StrokeType.CENTERED, thickness.toDouble(),
                                  RenderingHints.VALUE_ANTIALIAS_ON)
  }
}
