// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.impl.RegistryFontLookup
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.FontMetrics
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform

private val FAMILY_STYLES = intArrayOf(Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD or Font.ITALIC)

/**
 * Checks the font chain of [TextFonts.forFontFamilies]: the font families first, then the editor fonts.
 */
@TestApplication
internal class TextFontsForFontFamiliesTest {
  private val preferences: FontPreferences = FontPreferencesImpl().apply { register(Font.MONOSPACED, 13) }.snapshot
  private val context = FontRenderContext(null, true, true)

  @Test
  fun `ASCII text gets the first font family in each style`() {
    val fonts = TextFonts.forFontFamilies(listOf(Font.SERIF, Font.SANS_SERIF), preferences, context, null)
    for (style in FAMILY_STYLES) {
      assertThat(fonts.fontAbleToDisplay("a".toCharArray(), 0, 1, style, context)).isSameAs(familyFont(Font.SERIF, style))
      assertThat(fonts.fontAbleToDisplay("a", 0, 1, style, context)).isSameAs(familyFont(Font.SERIF, style))
    }
  }

  @Test
  fun `an unknown style gets the plain font of the family`() {
    val fonts = TextFonts.forFontFamilies(listOf(Font.SERIF), preferences, context, null)
    assertThat(fonts.fontAbleToDisplay("a", 0, 1, 42, context)).isSameAs(familyFont(Font.SERIF, Font.PLAIN))
  }

  @Test
  fun `a code point that the first family does not display gets the next family`() {
    val pair = findFamilyPair()
    assumeTrue(pair != null, "Each installed font family displays the test code points, for example on macOS, or none does")
    val fonts = TextFonts.forFontFamilies(listOf(pair!!.withoutCodePoint, pair.withCodePoint), preferences, context, null)
    val chars = Character.toChars(pair.codePoint)
    assertThat(fonts.fontAbleToDisplay(chars, 0, chars.size, Font.PLAIN, context)).isSameAs(familyFont(pair.withCodePoint, Font.PLAIN))
    assertThat(fonts.fontAbleToDisplay("a", 0, 1, Font.PLAIN, context)).isSameAs(familyFont(pair.withoutCodePoint, Font.PLAIN))
  }

  @Test
  fun `a code point that no font family displays gets the editor fonts`() {
    val fonts = TextFonts.forFontFamilies(listOf(Font.SERIF, Font.SANS_SERIF), preferences, context, null)
    val chars = Character.toChars(UNDISPLAYABLE)
    val editorFont = RegistryFontLookup(preferences).fontAbleToDisplay(chars, 0, chars.size, Font.BOLD, context)
    assertThat(fonts.fontAbleToDisplay(chars, 0, chars.size, Font.BOLD, context)).isSameAs(editorFont)
    assertThat(fonts.fontAbleToDisplay(String(chars), 0, chars.size, Font.BOLD, context)).isSameAs(editorFont)
  }

  @Test
  fun `a grapheme of two code points gets the first family that displays both`() {
    val fonts = TextFonts.forFontFamilies(listOf(Font.SERIF), preferences, context, null)
    val text = "e\u0301"
    assertThat(fonts.fontAbleToDisplay(text.toCharArray(), 0, 2, Font.PLAIN, context)).isSameAs(familyFont(Font.SERIF, Font.PLAIN))
    assertThat(fonts.fontAbleToDisplay(text, 0, 2, Font.PLAIN, context)).isSameAs(familyFont(Font.SERIF, Font.PLAIN))
  }

  @Test
  fun `a grapheme of one high surrogate gets the same font from a char array and from a char sequence`() {
    val fonts = TextFonts.forFontFamilies(listOf(Font.SERIF), preferences, context, null)
    val text = "x" + String(Character.toChars(GRINNING_FACE))
    val fromArray = fonts.fontAbleToDisplay(text.toCharArray(), 1, 2, Font.PLAIN, context)
    assertThat(fonts.fontAbleToDisplay(text, 1, 2, Font.PLAIN, context)).isSameAs(fromArray)
  }

  @Test
  fun `a monospaced family follows the ligature setting of the editor, a proportional family has no ligatures`() {
    val withLigatures = FontPreferencesImpl().apply {
      register(Font.MONOSPACED, 13)
      setUseLigatures(true)
    }.snapshot
    assertThat(FontFamilyService.isMonospaced(Font.MONOSPACED)).isTrue()
    assertThat(FontFamilyService.isMonospaced(Font.SERIF)).isFalse()
    val monospaced = TextFonts.forFontFamilies(listOf(Font.MONOSPACED), withLigatures, context, null)
    assertThat(monospaced.fontAbleToDisplay("a", 0, 1, Font.PLAIN, context))
      .isSameAs(ComplementaryFontsRegistry.getFamilyFontInfo(Font.MONOSPACED, Font.PLAIN, 13f, true, context))
    val proportional = TextFonts.forFontFamilies(listOf(Font.SERIF), withLigatures, context, null)
    assertThat(proportional.fontAbleToDisplay("a", 0, 1, Font.PLAIN, context)).isSameAs(familyFont(Font.SERIF, Font.PLAIN))
  }

  @Test
  fun `the families use the editor size`() {
    val large = FontPreferencesImpl().apply { register(Font.MONOSPACED, 20) }.snapshot
    val fonts = TextFonts.forFontFamilies(listOf(Font.SERIF), large, context, null)
    assertThat(fonts.fontAbleToDisplay("a", 0, 1, Font.PLAIN, context).size2D).isEqualTo(20f)
  }

  @Test
  fun `another render context gets the fonts of that context`() {
    val fonts = TextFonts.forFontFamilies(listOf(Font.SERIF), preferences, context, null)
    val otherContext = FontRenderContext(AffineTransform.getScaleInstance(2.0, 2.0), true, true)
    val font = fonts.fontAbleToDisplay("a", 0, 1, Font.PLAIN, otherContext)
    assertThat(font).isSameAs(ComplementaryFontsRegistry.getFamilyFontInfo(Font.SERIF, Font.PLAIN, 13f, false, otherContext))
    assertThat(font).isNotSameAs(familyFont(Font.SERIF, Font.PLAIN))
  }

  @Test
  fun `the widths and the bounds come from the font families`() {
    FontLayoutService.setInstance(FamilyMetricsFontLayoutService(FontFamilyService.getFont(Font.SERIF, null, null, Font.PLAIN).family))
    try {
      val fonts = TextFonts.forFontFamilies(listOf(Font.SERIF, Font.SANS_SERIF), preferences, context, null)
      assertThat(fonts.codePointWidth('a'.code, Font.PLAIN)).isEqualTo(SERIF_WIDTH)
      assertThat(fonts.codePointWidth(CYRILLIC_ZHE, Font.BOLD)).isEqualTo(SERIF_WIDTH)
      assertThat(fonts.codePointWidth(UNDISPLAYABLE, Font.BOLD)).isEqualTo(OTHER_WIDTH)
      assertThat(fonts.maxPrintableAsciiWidth()).isEqualTo(SERIF_WIDTH)
      assertThat(fonts.maxCharWidth()).isEqualTo(SERIF_WIDTH)
      assertThat(fonts.ascent()).isEqualTo(SERIF_HEIGHT - SERIF_DESCENT)
      assertThat(fonts.descent()).isEqualTo(OTHER_DESCENT)
      val editorFonts = TextFonts(preferences, context, null)
      assertThat(editorFonts.codePointWidth('a'.code, Font.PLAIN)).isEqualTo(OTHER_WIDTH)
      assertThat(editorFonts.ascent()).isZero()
      assertThat(editorFonts.descent()).isZero()
      assertThat(editorFonts.maxCharWidth()).isZero()
    }
    finally {
      FontLayoutService.setInstance(null)
    }
  }

  @Test
  fun `the list of font families must not be empty`() {
    assertThatThrownBy { TextFonts.forFontFamilies(listOf(), preferences, context, null) }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `parallel searches give the first family that displays each code point`(): Unit = timeoutRunBlocking {
    val familyNames = listOf(Font.SERIF, Font.SANS_SERIF)
    val codePoints = (0x20 until 0x3000).toList() + UNDISPLAYABLE
    val expected = codePoints.map { expectedFont(familyNames, it) }
    val fonts = TextFonts.forFontFamilies(familyNames, preferences, context, null)
    val results = (1..8).map {
      async(Dispatchers.Default) {
        repeat(2) { codePoints.forEach { search(fonts, it) } }
        codePoints.map { search(fonts, it) }
      }
    }.awaitAll()
    for (result in results) {
      assertThat(result).isEqualTo(expected)
    }
  }

  private fun search(fonts: TextFonts, codePoint: Int): FontInfo {
    val chars = Character.toChars(codePoint)
    return fonts.fontAbleToDisplay(chars, 0, chars.size, Font.ITALIC, context)
  }

  /**
   * Returns the font that the chain rule gives for [codePoint] in italic, without any cache.
   */
  private fun expectedFont(familyNames: List<String>, codePoint: Int): FontInfo {
    if (codePoint < 128) {
      return familyFont(familyNames.first(), Font.ITALIC)
    }
    for (name in familyNames) {
      val font = familyFont(name, Font.ITALIC)
      if (FontInfo.canDisplay(font.font, codePoint, true)) {
        return font
      }
    }
    val chars = Character.toChars(codePoint)
    return RegistryFontLookup(preferences).fontAbleToDisplay(chars, 0, chars.size, Font.ITALIC, context)
  }

  private fun familyFont(family: String, style: Int): FontInfo {
    return ComplementaryFontsRegistry.getFamilyFontInfo(family, style, 13f, false, context)
  }

  /**
   * Finds two installed font families and a code point that the first family does not display and the second one does.
   */
  private fun findFamilyPair(): FamilyPair? {
    val families = FontFamilyService.getAvailableFamilies()
    for (codePoint in intArrayOf(0x6211, 0x0436, 0x03B1, 0x05D0, 0x0E01)) {
      val withCodePoint = families.firstOrNull { displays(it, codePoint) } ?: continue
      val withoutCodePoint = families.firstOrNull { it != withCodePoint && !displays(it, codePoint) } ?: continue
      return FamilyPair(withoutCodePoint, withCodePoint, codePoint)
    }
    return null
  }

  private fun displays(family: String, codePoint: Int): Boolean {
    return FontInfo.canDisplay(FontFamilyService.getFont(family, null, null, Font.PLAIN), codePoint, true)
  }

  private class FamilyPair(val withoutCodePoint: String, val withCodePoint: String, val codePoint: Int)
}

private const val CYRILLIC_ZHE = 0x0436
private const val UNDISPLAYABLE = 0x10FFFD
private const val GRINNING_FACE = 0x1F600
private const val SERIF_WIDTH = 7f
private const val SERIF_HEIGHT = 30
private const val SERIF_DESCENT = 4
private const val OTHER_WIDTH = 10f
private const val OTHER_HEIGHT = 20
private const val OTHER_DESCENT = 6

/**
 * Gives the fonts of one family other widths and metrics than all other fonts.
 */
private class FamilyMetricsFontLayoutService(private val family: String) : MockFontLayoutService(10, OTHER_HEIGHT, OTHER_DESCENT) {
  override fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float {
    return if (fontMetrics.font.family == family) SERIF_WIDTH else OTHER_WIDTH
  }

  override fun getHeight(fontMetrics: FontMetrics): Int {
    return if (fontMetrics.font.family == family) SERIF_HEIGHT else OTHER_HEIGHT
  }

  override fun getDescent(fontMetrics: FontMetrics): Int {
    return if (fontMetrics.font.family == family) SERIF_DESCENT else OTHER_DESCENT
  }
}
