// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines.ui

import com.intellij.openapi.util.registry.Registry
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D

@Suppress("UseJBColor") // JBColor does not work properly if IDE theme differs from editor's color scheme
internal class StickyLineShadowPainter(var isDarkColorScheme: Boolean = false) {

  // shadow settings
  private val SHADOW_HEIGHT_FACTOR_LIGHT: Double = 0.2
  private val SHADOW_HEIGHT_FACTOR_DARK : Double = 0.3
  private val SHADOW_COLOR_ALPHA_LIGHT  : Int = 13
  private val SHADOW_COLOR_ALPHA_DARK   : Int = 45
  private val SHADOW_COLOR_LIGHT       = Color(0, 0, 0, SHADOW_COLOR_ALPHA_LIGHT)
  private val SHADOW_COLOR_DARK        = Color(0, 0, 0, SHADOW_COLOR_ALPHA_DARK)
  private val SHADOW_COLOR_TRANSPARENT = Color(0, 0, 0, 0)

  @Suppress("GraphicsSetClipInspection")
  fun paintShadow(g: Graphics2D, panelHeight: Int, panelWidth: Int, lineHeight: Int) {
    if (isEnabled()) {
      val shadowHeight = shadowHeight(lineHeight)
      val prevPaint = g.paint
      g.setClip(0, 0, panelWidth, panelHeight + shadowHeight)
      g.translate(0, panelHeight)
      g.paint = GradientPaint(
        0.0f,
        0.0f,
        shadowColor(),
        0.0f,
        shadowHeight.toFloat(),
        SHADOW_COLOR_TRANSPARENT
      )
      g.fillRect(0, 0, panelWidth, shadowHeight)
      g.paint = prevPaint
      g.translate(0, -panelHeight)
      g.setClip(0, 0, panelWidth, panelHeight)
    }
  }

  private fun shadowHeight(lineHeight: Int): Int {
    val factor = if (isDarkColorScheme) SHADOW_HEIGHT_FACTOR_DARK else SHADOW_HEIGHT_FACTOR_LIGHT
    return (lineHeight * factor).toInt()
  }

  private fun shadowColor(): Color {
    return if (isDarkColorScheme) SHADOW_COLOR_DARK else SHADOW_COLOR_LIGHT
  }

  private fun isEnabled(): Boolean {
    return Registry.`is`("editor.show.sticky.lines.shadow", true)
  }
}
