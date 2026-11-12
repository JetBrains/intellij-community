// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.font.TextAttribute
import java.util.function.IntToDoubleFunction

private const val COMBINING_ACUTE = 0x301

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
  fun `other letter spacing gives a new font info with the tracking`() {
    val plain = fallback.getFontInfo(13f, false, 0f, setOf(), null)
    val spaced = fallback.getFontInfo(13f, false, 0.1f, setOf(), null)
    assertThat(spaced).isNotSameAs(plain)
    assertThat(spaced.font.attributes[TextAttribute.TRACKING]).isEqualTo(0.1f)
    assertThat(plain.font.attributes[TextAttribute.TRACKING]).isNotEqualTo(0.1f)
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
