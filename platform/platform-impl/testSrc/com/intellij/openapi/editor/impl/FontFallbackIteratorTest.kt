// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.impl.view.TextFonts
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.font.FontRenderContext

private val STYLES = intArrayOf(Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD or Font.ITALIC)

/**
 * Checks the ranges of [FontFallbackIterator] for the printable ASCII font of a [FontLookup].
 */
@TestApplication
internal class FontFallbackIteratorTest {
  private val preferences: FontPreferences = FontPreferencesImpl().apply { register(Font.MONOSPACED, 13) }.snapshot
  private val context = FontRenderContext(null, true, true)
  private val registry = RegistryFontLookup(preferences)
  private val asciiFont = registry.fontAbleToDisplay("a", 0, 1, Font.PLAIN, context)
  private val otherFont = registry.fontAbleToDisplay("a", 0, 1, Font.BOLD, context)

  @Test
  fun `printable ASCII text is one range with the printable ASCII font of the lookup`() {
    val lookup = TwoFontsLookup(asciiFont)
    assertThat(ranges(lookup, " a~Z")).containsExactly(FontRange(0, 4, asciiFont))
    assertThat(rangesOfSequence(lookup, "x a~Z", 1, 5)).containsExactly(FontRange(1, 5, asciiFont))
    assertThat(lookup.searches).isZero()
  }

  @Test
  fun `text with a char that is not printable ASCII gets a font for each char`() {
    for (text in listOf("a\u0001b", "a\tb", "a\u007Fb", "aéb", "é")) {
      val lookup = TwoFontsLookup(asciiFont)
      assertThat(ranges(lookup, text)).describedAs(text).isEqualTo(ranges(TwoFontsLookup(null), text))
      assertThat(lookup.searches).describedAs(text).isPositive()
    }
  }

  @Test
  fun `a lookup without a printable ASCII font gets a font for each char`() {
    val lookup = TwoFontsLookup(null)
    assertThat(ranges(lookup, "ab")).containsExactly(FontRange(0, 2, asciiFont))
    assertThat(lookup.searches).isEqualTo(2)
  }

  @Test
  fun `an empty range has no ranges`() {
    val lookup = TwoFontsLookup(asciiFont)
    assertThat(ranges(lookup, "")).isEmpty()
    assertThat(rangesOfSequence(lookup, "ab", 1, 1)).isEmpty()
  }

  @Test
  fun `the ranges of the editor fonts are the ranges of the fallback search`() {
    val fonts = TextFonts(preferences, context, null)
    val texts = listOf(
      "Lorem ipsum dolor sit amet.",
      "a\tb",
      "x = 1; // é",
      "\u0001",
      "abć",
      "a" + String(Character.toChars(0x1F600)) + "b",
      "жж ab",
    )
    for (style in STYLES) {
      for (text in texts) {
        assertThat(ranges(fonts, text, style)).describedAs("%s in style %d", text, style).isEqualTo(ranges(registry, text, style))
        assertThat(rangesOfSequence(fonts, text, 1, text.length, style)).describedAs("%s in style %d", text, style)
          .isEqualTo(rangesOfSequence(registry, text, 1, text.length, style))
      }
    }
  }

  private fun ranges(lookup: FontLookup, text: String, style: Int = Font.PLAIN): List<FontRange> {
    val iterator = FontFallbackIterator(lookup, context, style)
    iterator.start(text.toCharArray(), 0, text.length)
    return collect(iterator)
  }

  private fun rangesOfSequence(lookup: FontLookup, text: String, start: Int, end: Int, style: Int = Font.PLAIN): List<FontRange> {
    val iterator = FontFallbackIterator(lookup, context, style)
    iterator.start(text, start, end)
    return collect(iterator)
  }

  private fun collect(iterator: FontFallbackIterator): List<FontRange> {
    val result = ArrayList<FontRange>()
    while (!iterator.atEnd()) {
      result.add(FontRange(iterator.getStart(), iterator.getEnd(), iterator.getFontInfo()))
      iterator.advance()
    }
    return result
  }

  private data class FontRange(val start: Int, val end: Int, val font: FontInfo)

  /**
   * Gives one font to the printable ASCII chars and another font to all other graphemes, and counts the searches.
   */
  private inner class TwoFontsLookup(private val printableAsciiFont: FontInfo?) : FontLookup {
    var searches: Int = 0
      private set

    override fun fontAbleToDisplay(text: CharArray, start: Int, end: Int, style: Int, context: FontRenderContext?): FontInfo {
      return search(String(text, start, end - start))
    }

    override fun fontAbleToDisplay(text: CharSequence, start: Int, end: Int, style: Int, context: FontRenderContext?): FontInfo {
      return search(text.substring(start, end))
    }

    override fun printableAsciiFont(style: Int, context: FontRenderContext?): FontInfo? {
      return printableAsciiFont
    }

    private fun search(grapheme: String): FontInfo {
      searches++
      return if (grapheme.length == 1 && grapheme[0] in ' '..'~') asciiFont else otherFont
    }
  }
}
