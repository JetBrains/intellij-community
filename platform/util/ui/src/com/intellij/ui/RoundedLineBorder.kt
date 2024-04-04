// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import java.awt.*
import javax.swing.border.LineBorder

open class RoundedLineBorder : LineBorder {
  private var myArcSize = 1

  constructor(color: Color?) : super(color)

  @JvmOverloads
  constructor(color: Color?, arcSize: Int, thickness: Int = 1) : super(color, thickness) {
    myArcSize = arcSize
  }

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2 = g as Graphics2D

    val oldAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val oldColor = g2.color
    g2.color = lineColor

    for (i in 0 until thickness) {
      g2.drawRoundRect(x + i, y + i, width - i - i - 1, height - i - i - 1, myArcSize, myArcSize)
    }

    g2.color = oldColor
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing)
  }

  fun setColor(color: Color) {
    lineColor = color
  }
}
