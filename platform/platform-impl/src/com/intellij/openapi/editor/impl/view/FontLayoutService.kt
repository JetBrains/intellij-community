// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.jetbrains.FontMetricsAccessor
import com.jetbrains.JBR
import org.jetbrains.annotations.ApiStatus
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
  abstract fun layoutGlyphVector(
    font: Font,
    fontRenderContext: FontRenderContext,
    chars: CharArray,
    start: Int,
    end: Int,
    isRtl: Boolean,
  ): GlyphVector

  /**
   * Returns the glyphs of the chars without the text layout: one glyph for each char, in the order of the chars.
   * Use it only for left-to-right text in a font without layout attributes, see [Font.hasLayoutAttributes]. The text
   * must have no surrogate pair, no mark, no format char and no complex script, see [Font.textRequiresLayout].
   * The default calls [layoutGlyphVector].
   */
  @ApiStatus.Internal
  open fun createGlyphVector(
    font: Font,
    fontRenderContext: FontRenderContext,
    chars: CharArray,
    start: Int,
    end: Int,
  ): GlyphVector {
    return layoutGlyphVector(font, fontRenderContext, chars, start, end, false)
  }

  abstract fun charWidth(fontMetrics: FontMetrics, c: Char): Int

  abstract fun charWidth(fontMetrics: FontMetrics, codePoint: Int): Int

  abstract fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float

  abstract fun stringWidth(fontMetrics: FontMetrics, str: String): Int

  abstract fun getHeight(fontMetrics: FontMetrics): Int

  abstract fun getDescent(fontMetrics: FontMetrics): Int

  companion object {
    @Volatile
    private var INSTANCE: FontLayoutService = DefaultFontLayoutService

    @JvmStatic
    fun getInstance(): FontLayoutService = INSTANCE

    @TestOnly
    @JvmStatic
    fun setInstance(fontLayoutService: FontLayoutService?) {
      setInstanceInternal(fontLayoutService)
    }

    @ApiStatus.Internal
    fun setInstanceInternal(fontLayoutService: FontLayoutService?) {
      INSTANCE = fontLayoutService ?: DefaultFontLayoutService
    }
  }
}

private object DefaultFontLayoutService : FontLayoutService() {
  private val fontMetricsAccessor: FontMetricsAccessor? = JBR.getFontMetricsAccessor()

  override fun layoutGlyphVector(
    font: Font,
    fontRenderContext: FontRenderContext,
    chars: CharArray,
    start: Int,
    end: Int,
    isRtl: Boolean,
  ): GlyphVector {
    return font.layoutGlyphVector(
      /* frc = */ fontRenderContext,
      /* text = */ chars,
      /* start = */ start,
      /* limit = */ end,
      /* flags = */ if (isRtl) Font.LAYOUT_RIGHT_TO_LEFT else Font.LAYOUT_LEFT_TO_RIGHT,
    )
  }

  override fun createGlyphVector(
    font: Font,
    fontRenderContext: FontRenderContext,
    chars: CharArray,
    start: Int,
    end: Int,
  ): GlyphVector {
    val text = if (start == 0 && end == chars.size) chars else chars.copyOfRange(start, end)
    return font.createGlyphVector(fontRenderContext, text)
  }

  override fun charWidth(fontMetrics: FontMetrics, c: Char): Int = fontMetrics.charWidth(c)

  override fun charWidth(fontMetrics: FontMetrics, codePoint: Int): Int = fontMetrics.charWidth(codePoint)

  override fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float {
    return fontMetricsAccessor?.codePointWidth(fontMetrics, codePoint)
           ?: throw UnsupportedOperationException("current runtime does not support FontMetricsAccessor")
  }

  override fun stringWidth(fontMetrics: FontMetrics, str: String): Int = fontMetrics.stringWidth(str)

  override fun getHeight(fontMetrics: FontMetrics): Int = fontMetrics.height

  override fun getDescent(fontMetrics: FontMetrics): Int = fontMetrics.descent
}
