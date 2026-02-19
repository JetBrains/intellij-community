// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines.ui

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.registry.Registry
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.border.LineBorder


internal class StickyLineShadowBorder(
  private val editor: EditorEx,
  private val colors: StickyLineColors,
  private val shadowPainter: StickyLineShadowPainter,
) : LineBorder(null, 1) {

  fun borderHeight(): Int {
    return thickness + shadowHeight()
  }

  override fun getLineColor(): Color {
    return colors.borderLineColor()
  }

  override fun getBorderInsets(c: Component?, insets: Insets): Insets {
    insets.set(0, 0, thickness, 0)
    return insets
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    paintLineBorder(g, x, y, width, height)
    if (isShadowEnabled()) {
      shadowPainter.paintShadow(g as Graphics2D, x, y, width, height, shadowHeight())
    }
  }

  private fun paintLineBorder(g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val oldColor = g.color
    g.color = getLineColor()
    g.fillRect(x, y + (height - borderHeight()), width, thickness)
    g.color = oldColor
  }

  private fun shadowHeight(): Int {
    return if (isShadowEnabled()) {
      (colors.heightFactor() * editor.lineHeight).toInt()
    } else {
      0
    }
  }

  private fun isShadowEnabled(): Boolean {
    return Registry.`is`("editor.show.sticky.lines.shadow", true)
  }
}
