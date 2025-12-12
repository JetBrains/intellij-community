// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines.ui

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ColorUtil
import java.awt.Color


@Suppress("UseJBColor") // JBColor does not work properly if IDE theme differs from editor's color scheme
internal class StickyLineColors(private var scheme: EditorColorsScheme) {

  private var isDarkColorScheme = isDarkColorScheme()

  fun updateScheme(newScheme: EditorColorsScheme): Boolean {
    val oldIsDark = isDarkColorScheme
    this.scheme = newScheme
    this.isDarkColorScheme = isDarkColorScheme()
    return oldIsDark != isDarkColorScheme
  }

  fun borderLineColor(): Color {
    return if (isDebugEnabled()) {
      BORDER_LINE_COLOR_DEBUG
    } else {
      val stickyLinesBorderColor = scheme.getColor(EditorColors.STICKY_LINES_BORDER_COLOR)
      stickyLinesBorderColor ?: scheme.defaultBackground
    }
  }

  fun shadowColor(): Color {
    return if (isDebugEnabled()) {
      if (isDarkColorScheme) SHADOW_COLOR_DARK_DEBUG else SHADOW_COLOR_LIGHT_DEBUG
    } else {
      if (isDarkColorScheme) SHADOW_COLOR_DARK else SHADOW_COLOR_LIGHT
    }
  }

  fun shadowTransparentColor(): Color {
    return if (isDebugEnabled()) {
      SHADOW_COLOR_TRANSPARENT_DEBUG
    } else {
      SHADOW_COLOR_TRANSPARENT
    }
  }

  fun heightFactor(): Double {
    return if (isDarkColorScheme) {
      SHADOW_HEIGHT_FACTOR_DARK
    } else {
      SHADOW_HEIGHT_FACTOR_LIGHT
    }
  }

  private fun isDarkColorScheme(): Boolean {
    val background = scheme.defaultBackground
    return ColorUtil.isDark(background)
  }

  private fun isDebugEnabled(): Boolean {
    return Registry.`is`("editor.show.sticky.lines.shadow.debug", false)
  }

  companion object {
    private const val SHADOW_HEIGHT_FACTOR_LIGHT = 0.17
    private const val SHADOW_HEIGHT_FACTOR_DARK = 0.25
    private const val SHADOW_COLOR_ALPHA_LIGHT = 8
    private const val SHADOW_COLOR_ALPHA_DARK = 32

    private val SHADOW_COLOR_LIGHT = Color(0, 0, 0, SHADOW_COLOR_ALPHA_LIGHT)
    private val SHADOW_COLOR_DARK = Color(0, 0, 0, SHADOW_COLOR_ALPHA_DARK)
    private val SHADOW_COLOR_TRANSPARENT = Color(0, 0, 0, 0)

    private val SHADOW_COLOR_LIGHT_DEBUG = Color.GREEN
    private val SHADOW_COLOR_DARK_DEBUG = Color.BLUE
    private val SHADOW_COLOR_TRANSPARENT_DEBUG = Color.RED
    private val BORDER_LINE_COLOR_DEBUG = Color.YELLOW
  }
}
