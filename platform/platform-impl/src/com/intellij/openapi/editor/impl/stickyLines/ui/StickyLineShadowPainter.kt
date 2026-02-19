// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines.ui

import java.awt.GradientPaint
import java.awt.Graphics2D


internal class StickyLineShadowPainter(private val colors: StickyLineColors) {

  fun paintShadow(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, shadowHeight: Int) {
    val tmpPaint =  g.paint
    val x1 = x.toFloat()
    val y1 = (y + height - shadowHeight).toFloat()
    val color1 = colors.shadowColor()
    val x2 = x.toFloat()
    val y2 = (y + height).toFloat()
    val color2 = colors.shadowTransparentColor()
    g.paint = GradientPaint(x1, y1, color1, x2, y2, color2)
    g.fillRect(x, y + height - shadowHeight, width, height)
    g.paint = tmpPaint
  }
}
