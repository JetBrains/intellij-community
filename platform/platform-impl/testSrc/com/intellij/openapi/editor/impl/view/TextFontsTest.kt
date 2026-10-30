// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.FontMetrics
import java.awt.font.FontRenderContext

private val STYLES = intArrayOf(Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD or Font.ITALIC)
private val PRINTABLE_ASCII = ' '.code..'~'.code

/** More code points than the lossy cache has entries, so that a width replaces another one. */
private val OTHER_CODE_POINTS = 0x80 until 0x1080
private val CODE_POINTS: List<Int> = PRINTABLE_ASCII.toList() + OTHER_CODE_POINTS.toList()

/**
 * Checks the widths of [TextFonts] against the font that the fallback search gives.
 * [StyleWidthFontLayoutService] gives each code point and style its own width, so a width of another key is visible.
 */
@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class TextFontsTest {
  private val preferences: FontPreferences = FontPreferencesImpl().apply { register(Font.MONOSPACED, 13) }.snapshot
  private val context = FontRenderContext(null, true, true)

  @BeforeEach
  fun setUp() {
    FontLayoutService.setInstance(StyleWidthFontLayoutService())
  }

  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `each width is the width of the font from the fallback search`() {
    val fonts = TextFonts(preferences, context, null)
    // the second pass reads the cached widths
    repeat(2) {
      for (style in STYLES) {
        for (codePoint in CODE_POINTS) {
          assertThat(fonts.codePointWidth(codePoint, style)).describedAs("U+%04X in style %d", codePoint, style)
            .isEqualTo(searchWidth(codePoint, style))
        }
      }
    }
  }

  @Test
  fun `an unknown style gives the plain widths`() {
    val fonts = TextFonts(preferences, context, null)
    assertThat(fonts.codePointWidth('a'.code, 42)).isEqualTo(searchWidth('a'.code, Font.PLAIN))
    assertThat(fonts.codePointWidth(0x3B1, -1)).isEqualTo(searchWidth(0x3B1, Font.PLAIN))
  }

  @Test
  fun `a special character gets the width of its code when the editor shows special characters`() {
    val specialCharacterFont = Font(Font.MONOSPACED, Font.PLAIN, 13)
    val fonts = TextFonts(preferences, context, specialCharacterFont)
    val nonBreakingSpace = 0xA0
    assertThat(fonts.codePointWidth(nonBreakingSpace, Font.PLAIN))
      .isEqualTo(SpecialCharacterFragment.getCodeWidth(specialCharacterFont, context, nonBreakingSpace))
      .isNotEqualTo(searchWidth(nonBreakingSpace, Font.PLAIN))
    assertThat(fonts.codePointWidth('a'.code, Font.PLAIN)).isEqualTo(searchWidth('a'.code, Font.PLAIN))
  }

  @Test
  fun `the maximum printable ASCII width is the widest character in any style`() {
    val expected = STYLES.maxOf { style -> PRINTABLE_ASCII.maxOf { searchWidth(it, style) } }
    assertThat(TextFonts(preferences, context, null).maxPrintableAsciiWidth()).isEqualTo(expected)
  }

  @Test
  fun `parallel readers get the widths of the fallback search`(): Unit = timeoutRunBlocking {
    val fonts = TextFonts(preferences, context, null)
    val expected = CODE_POINTS.map { searchWidth(it, Font.BOLD) }
    val results = (1..8).map {
      async(Dispatchers.Default) {
        CODE_POINTS.map { fonts.codePointWidth(it, Font.BOLD) }
      }
    }.awaitAll()
    for (result in results) {
      assertThat(result).isEqualTo(expected)
    }
  }

  private fun searchWidth(codePoint: Int, style: Int): Float {
    return ComplementaryFontsRegistry.getFontAbleToDisplay(codePoint, style, preferences, context).charWidth2D(codePoint)
  }
}

/**
 * Gives each code point and font style its own width.
 */
private class StyleWidthFontLayoutService : MockFontLayoutService(10, 10, 2) {
  override fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float {
    return (1 + codePoint % 101 + 1000 * fontMetrics.font.style).toFloat()
  }
}
