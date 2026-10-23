// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font

@TestApplication
internal class FontFallbackTest {
  private val fallback = FontFallback(Font(Font.MONOSPACED, Font.PLAIN, 1))

  @Test
  fun `same arguments give the same font info`() {
    val info = fallback.getFontInfo(13f, false, setOf(), null)
    assertThat(fallback.getFontInfo(13f, false, setOf(), null)).isSameAs(info)
  }

  @Test
  fun `font info comes back from the cache after another size`() {
    val info13 = fallback.getFontInfo(13f, false, setOf(), null)
    val info14 = fallback.getFontInfo(14f, false, setOf(), null)
    assertThat(info14).isNotSameAs(info13)
    assertThat(info14.size2D).isEqualTo(14f)
    assertThat(fallback.getFontInfo(13f, false, setOf(), null)).isSameAs(info13)
  }

  @Test
  fun `equal variant sets in another order give the same font info`() {
    val info = fallback.getFontInfo(13f, false, linkedSetOf("ss01", "zero"), null)
    assertThat(fallback.getFontInfo(13f, false, linkedSetOf("zero", "ss01"), null)).isSameAs(info)
    fallback.getFontInfo(14f, false, setOf(), null)
    assertThat(fallback.getFontInfo(13f, false, linkedSetOf("zero", "ss01"), null)).isSameAs(info)
  }

  @Test
  fun `other variants give a new font info`() {
    val plain = fallback.getFontInfo(13f, false, setOf(), null)
    val withZero = fallback.getFontInfo(13f, false, setOf("zero"), null)
    assertThat(withZero).isNotSameAs(plain)
    assertThat(fallback.getFontInfo(13f, false, setOf(), null)).isSameAs(plain)
  }

  @Test
  fun `a change of the variants after a call does not change the cache`() {
    val variants = linkedSetOf("ss01")
    val info = fallback.getFontInfo(13f, false, variants, null)
    variants.add("zero")
    assertThat(fallback.getFontInfo(13f, false, setOf("ss01"), null)).isSameAs(info)
  }
}
