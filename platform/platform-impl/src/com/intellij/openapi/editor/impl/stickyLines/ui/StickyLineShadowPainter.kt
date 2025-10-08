// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines.ui

import com.intellij.openapi.util.registry.Registry
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D


@Suppress("UseJBColor") // JBColor does not work properly if IDE theme differs from editor's color scheme
internal class StickyLineShadowPainter(var isDarkColorScheme: Boolean = false) {

  // shadow settings
  private val SHADOW_HEIGHT_FACTOR_LIGHT: Double = 0.17
  private val SHADOW_HEIGHT_FACTOR_DARK: Double = 0.25
  private val SHADOW_COLOR_ALPHA_LIGHT: Int = 8
  private val SHADOW_COLOR_ALPHA_DARK: Int = 32
  private val SHADOW_COLOR_LIGHT = Color(0, 0, 0, SHADOW_COLOR_ALPHA_LIGHT)
  private val SHADOW_COLOR_DARK = Color(0, 0, 0, SHADOW_COLOR_ALPHA_DARK)
  private val SHADOW_COLOR_TRANSPARENT = Color(0, 0, 0, 0)

  fun paintShadow(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, shadowHeight: Int) {
    val tmpPaint =  g.paint
    g.paint = GradientPaint(
      x.toFloat(),
      (y + height - shadowHeight).toFloat(),
      shadowColor(),
      x.toFloat(),
      (y + height).toFloat(),
      shadowColorTransparent(),
    )
    g.fillRect(x, y + height - shadowHeight, width, height)
    g.paint = tmpPaint
  }

  fun shadowHeight(lineHeight: Int): Int {
    val factor = if (isDarkColorScheme) SHADOW_HEIGHT_FACTOR_DARK else SHADOW_HEIGHT_FACTOR_LIGHT
    return (lineHeight * factor).toInt()
  }

  private fun shadowColor(): Color {
    if (isShadowDebugEnabled()) {
      return Color.GREEN
    }
    return if (isDarkColorScheme) SHADOW_COLOR_DARK else SHADOW_COLOR_LIGHT
  }

  private fun shadowColorTransparent(): Color {
    if (isShadowDebugEnabled()) {
      return Color.RED
    }
    return SHADOW_COLOR_TRANSPARENT
  }

  fun isShadowDebugEnabled(): Boolean {
    return Registry.`is`("editor.show.sticky.lines.shadow.debug", false)
  }
}
