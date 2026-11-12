// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.testFramework.AbstractMockGlyphVector
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.GlyphMetrics
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D

internal class FontInfoLetterSpacingTest {
  @Test
  fun `with fractional metrics the letter spacing keeps its exact pixels`() {
    val context = FontRenderContext(null, true, true)
    assertThat(FontInfo.letterSpacingPixels(0.05f, 13f, context)).isCloseTo(0.65f, within(0.0001f))
  }

  @Test
  fun `without fractional metrics the letter spacing rounds to whole device pixels`() {
    val context = FontRenderContext(null, true, false)
    assertThat(FontInfo.letterSpacingPixels(0.05f, 13f, context)).isEqualTo(1f)
    assertThat(FontInfo.letterSpacingPixels(0.02f, 13f, context)).isEqualTo(0f)
    assertThat(FontInfo.letterSpacingPixels(-0.05f, 13f, context)).isEqualTo(-1f)
    assertThat(FontInfo.letterSpacingPixels(0.05f, 13f, null)).isEqualTo(1f)
  }

  @Test
  fun `a letter spacing of -0 gives 0`() {
    val pixels = FontInfo.letterSpacingPixels(-0f, 13f, FontRenderContext(null, true, true))
    assertThat(java.lang.Float.floatToRawIntBits(pixels)).isZero()
  }

  @Test
  fun `on a scaled context the letter spacing rounds to whole device pixels, not to user space pixels`() {
    val retina = FontRenderContext(AffineTransform.getScaleInstance(2.0, 2.0), true, false)
    assertThat(FontInfo.letterSpacingPixels(0.05f, 13f, retina)).isEqualTo(0.5f)
  }

  @Test
  fun `the left side bearing of a glyph comes from its metrics once for each glyph code`() {
    val fontInfo = FontInfo(Font(Font.MONOSPACED, Font.PLAIN, 1), 1f, true, 0f, setOf(), FontRenderContext(null, false, false))
    val ligature = Rectangle2D.Float(-3f, -5f, 6f, 5f)
    val glyphs = CountingGlyphVector(codes = intArrayOf(7, 7, 8), ink = listOf(ligature, ligature, Rectangle2D.Float()))

    assertThat(fontInfo.glyphLeftSideBearing(glyphs, 0)).isEqualTo(-3f)
    assertThat(fontInfo.glyphLeftSideBearing(glyphs, 1)).isEqualTo(-3f)
    assertThat(fontInfo.glyphLeftSideBearing(glyphs, 2)).isNaN()
    assertThat(fontInfo.glyphLeftSideBearing(glyphs, 2)).isNaN()
    assertThat(glyphs.metricsCalls).isEqualTo(2)
  }

  private class CountingGlyphVector(private val codes: IntArray, private val ink: List<Rectangle2D>) : AbstractMockGlyphVector() {
    var metricsCalls = 0
      private set

    override fun getNumGlyphs(): Int = codes.size

    override fun getGlyphCode(glyphIndex: Int): Int = codes[glyphIndex]

    override fun getGlyphMetrics(glyphIndex: Int): GlyphMetrics {
      metricsCalls++
      return GlyphMetrics(1f, ink[glyphIndex], GlyphMetrics.STANDARD)
    }
  }
}
