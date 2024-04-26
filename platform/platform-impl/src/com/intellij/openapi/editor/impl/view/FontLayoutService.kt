// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.jetbrains.JBR
import org.jetbrains.annotations.TestOnly
import java.awt.Font
import java.awt.FontMetrics
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector

/**
 * Encapsulates logic related to font metrics.
 * A Mock instance can be used in tests to make them independent on font properties on a particular platform.
 */
abstract class FontLayoutService {
  abstract fun layoutGlyphVector(font: Font,
                                 fontRenderContext: FontRenderContext,
                                 chars: CharArray,
                                 start: Int,
                                 end: Int,
                                 isRtl: Boolean): GlyphVector

  abstract fun charWidth(fontMetrics: FontMetrics, c: Char): Int

  abstract fun charWidth(fontMetrics: FontMetrics, codePoint: Int): Int

  abstract fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float

  abstract fun stringWidth(fontMetrics: FontMetrics, str: String): Int

  abstract fun getHeight(fontMetrics: FontMetrics): Int

  abstract fun getDescent(fontMetrics: FontMetrics): Int

  companion object {
    @JvmStatic
    fun getInstance(): FontLayoutService = INSTANCE

    @TestOnly
    @JvmStatic
    fun setInstance(fontLayoutService: FontLayoutService?) {
      INSTANCE = fontLayoutService ?: DEFAULT_INSTANCE
    }
  }
}

private val DEFAULT_INSTANCE: FontLayoutService = DefaultFontLayoutService()
private var INSTANCE = DEFAULT_INSTANCE

private class DefaultFontLayoutService : FontLayoutService() {
  private val fontMetricsAccessor = JBR.getFontMetricsAccessor()

  override fun layoutGlyphVector(font: Font,
                                 fontRenderContext: FontRenderContext,
                                 chars: CharArray,
                                 start: Int,
                                 end: Int,
                                 isRtl: Boolean): GlyphVector {
    return font.layoutGlyphVector(/* frc = */ fontRenderContext,
                                  /* text = */ chars,
                                  /* start = */ start,
                                  /* limit = */ end,
                                  /* flags = */ if (isRtl) Font.LAYOUT_RIGHT_TO_LEFT else Font.LAYOUT_LEFT_TO_RIGHT)
  }

  override fun charWidth(fontMetrics: FontMetrics, c: Char): Int = fontMetrics.charWidth(c)

  override fun charWidth(fontMetrics: FontMetrics, codePoint: Int): Int = fontMetrics.charWidth(codePoint)

  override fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float {
    return fontMetricsAccessor.codePointWidth(fontMetrics, codePoint)
  }

  override fun stringWidth(fontMetrics: FontMetrics, str: String): Int = fontMetrics.stringWidth(str)

  override fun getHeight(fontMetrics: FontMetrics): Int = fontMetrics.height

  override fun getDescent(fontMetrics: FontMetrics): Int = fontMetrics.descent
}