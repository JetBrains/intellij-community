// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font
import java.util.function.IntToDoubleFunction

private const val COMBINING_ACUTE = 0x301
private const val ARABIC_BEH = 0x628
private const val ARABIC_TATWEEL = 0x640
private const val ADLAM_ALIF = 0x1E900
private const val DEVANAGARI_NA = 0x928
private const val SYRIAC_ALAPH = 0x710
private const val MONGOLIAN_A = 0x1820
private const val HEBREW_SHIN = 0x5E9
private const val HEBREW_QAMATS = 0x5B8
private const val ZERO_WIDTH_JOINER = 0x200D
private const val CJK_SUN = 0x65E5

@TestApplication
internal class FontFallbackTest {
  private val fallback = FontFallback(Font(Font.MONOSPACED, Font.PLAIN, 1))

  @Test
  fun `same arguments give the same font info`() {
    val info = fallback.getFontInfo(13f, false, 0f, setOf(), null)
    assertThat(fallback.getFontInfo(13f, false, 0f, setOf(), null)).isSameAs(info)
  }

  @Test
  fun `font info comes back from the cache after another size`() {
    val info13 = fallback.getFontInfo(13f, false, 0f, setOf(), null)
    val info14 = fallback.getFontInfo(14f, false, 0f, setOf(), null)
    assertThat(info14).isNotSameAs(info13)
    assertThat(info14.size2D).isEqualTo(14f)
    assertThat(fallback.getFontInfo(13f, false, 0f, setOf(), null)).isSameAs(info13)
  }

  @Test
  fun `equal variant sets in another order give the same font info`() {
    val info = fallback.getFontInfo(13f, false, 0f, linkedSetOf("ss01", "zero"), null)
    assertThat(fallback.getFontInfo(13f, false, 0f, linkedSetOf("zero", "ss01"), null)).isSameAs(info)
    fallback.getFontInfo(14f, false, 0f, setOf(), null)
    assertThat(fallback.getFontInfo(13f, false, 0f, linkedSetOf("zero", "ss01"), null)).isSameAs(info)
  }

  @Test
  fun `other variants give a new font info`() {
    val plain = fallback.getFontInfo(13f, false, 0f, setOf(), null)
    val withZero = fallback.getFontInfo(13f, false, 0f, setOf("zero"), null)
    assertThat(withZero).isNotSameAs(plain)
    assertThat(fallback.getFontInfo(13f, false, 0f, setOf(), null)).isSameAs(plain)
  }

  @Test
  fun `other letter spacing gives a new font info with the same font`() {
    val plain = fallback.getFontInfo(13f, false, 0f, setOf(), null)
    val spaced = fallback.getFontInfo(13f, false, 0.1f, setOf(), null)
    assertThat(spaced).isNotSameAs(plain).isNotEqualTo(plain)
    assertThat(spaced.font).isEqualTo(plain.font)
    assertThat(fallback.getFontInfo(13f, false, 0f, setOf(), null)).isSameAs(plain)
  }

  @Test
  fun `the widths add the letter spacing to each code point with an advance`() {
    FontLayoutService.setInstance(MockFontLayoutService(IntToDoubleFunction { if (it == COMBINING_ACUTE) 0.0 else 7.0 }, 15, 3))
    try {
      val spaced = fallback.getFontInfo(8f, false, 0.25f, setOf(), null)
      assertThat(spaced.letterSpacingPixels).isEqualTo(2f)
      assertThat(spaced.charWidth2D('a'.code)).isEqualTo(9f)
      assertThat(spaced.charWidth('a'.code)).isEqualTo(9)
      assertThat(spaced.charWidth2D(COMBINING_ACUTE)).isEqualTo(0f)
      assertThat(spaced.takesLetterSpacing('a'.code)).isTrue()
      assertThat(spaced.takesLetterSpacing(COMBINING_ACUTE)).isFalse()
    } finally {
      FontLayoutService.setInstance(null)
    }
  }

  @Test
  fun `the letters of a script whose letters join take no letter spacing`() {
    FontLayoutService.setInstance(MockFontLayoutService(IntToDoubleFunction { 7.0 }, 15, 3))
    try {
      val spaced = fallback.getFontInfo(8f, false, 0.25f, setOf(), null)
      for (letter in listOf(ARABIC_BEH, ARABIC_TATWEEL, DEVANAGARI_NA, SYRIAC_ALAPH, MONGOLIAN_A, ADLAM_ALIF)) {
        assertThat(spaced.takesLetterSpacing(letter)).isFalse()
        assertThat(spaced.charWidth2D(letter)).isEqualTo(7f)
      }
      for (letter in listOf(HEBREW_SHIN, CJK_SUN, ' '.code)) {
        assertThat(spaced.takesLetterSpacing(letter)).isTrue()
        assertThat(spaced.charWidth2D(letter)).isEqualTo(9f)
      }
    } finally {
      FontLayoutService.setInstance(null)
    }
  }

  @Test
  fun `marks and format chars take no letter spacing whatever advance the font gives them`() {
    FontLayoutService.setInstance(MockFontLayoutService(IntToDoubleFunction { 7.0 }, 15, 3))
    try {
      val spaced = fallback.getFontInfo(8f, false, 0.25f, setOf(), null)
      for (codePoint in listOf(COMBINING_ACUTE, HEBREW_QAMATS, ZERO_WIDTH_JOINER)) {
        assertThat(spaced.takesLetterSpacing(codePoint)).isFalse()
        assertThat(spaced.charWidth2D(codePoint)).isEqualTo(7f)
      }
    } finally {
      FontLayoutService.setInstance(null)
    }
  }

  @Test
  fun `a negative letter spacing never makes a width below 0`() {
    FontLayoutService.setInstance(MockFontLayoutService(IntToDoubleFunction { if (it == 'i'.code) 3.0 else 7.0 }, 15, 3))
    try {
      val spaced = fallback.getFontInfo(8f, false, -0.5f, setOf(), null)
      assertThat(spaced.letterSpacingPixels).isEqualTo(-4f)
      assertThat(spaced.charWidth2D('a'.code)).isEqualTo(3f)
      assertThat(spaced.charWidth2D('i'.code)).isEqualTo(0f)
      assertThat(spaced.charWidth('i'.code)).isEqualTo(0)
      assertThat(spaced.letterSpacingAfter('i'.code, 3f)).isEqualTo(-3f)
    } finally {
      FontLayoutService.setInstance(null)
    }
  }

  @Test
  fun `a change of the variants after a call does not change the cache`() {
    val variants = linkedSetOf("ss01")
    val info = fallback.getFontInfo(13f, false, 0f, variants, null)
    variants.add("zero")
    assertThat(fallback.getFontInfo(13f, false, 0f, setOf("ss01"), null)).isSameAs(info)
  }
}
