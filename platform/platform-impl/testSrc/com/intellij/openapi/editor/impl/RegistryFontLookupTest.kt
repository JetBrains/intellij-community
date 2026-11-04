// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.font.FontRenderContext

@TestApplication
internal class RegistryFontLookupTest {
  private val preferences = FontPreferencesImpl().apply { register(Font.MONOSPACED, 13) }
  private val context = FontRenderContext(null, true, true)

  @Test
  fun `lookups of the same preferences are equal`() {
    val lookup = RegistryFontLookup(preferences)
    assertThat(RegistryFontLookup(preferences)).isEqualTo(lookup).hasSameHashCodeAs(lookup)
    assertThat(RegistryFontLookup(preferences.snapshot)).isEqualTo(lookup)
  }

  @Test
  fun `a change of the preferences gives another lookup`() {
    val lookup = RegistryFontLookup(preferences)
    preferences.setUseLigatures(!preferences.useLigatures())
    assertThat(RegistryFontLookup(preferences)).isNotEqualTo(lookup)
  }

  @Test
  fun `a later change of the preferences does not change the lookup`() {
    val lookup = RegistryFontLookup(preferences)
    val before = lookup.fontAbleToDisplay('a'.code, Font.PLAIN, context)
    preferences.setUseLigatures(!preferences.useLigatures())
    assertThat(lookup.fontAbleToDisplay('a'.code, Font.PLAIN, context)).isSameAs(before)
  }

  @Test
  fun `the lookup gives the fonts of the registry`() {
    val lookup = RegistryFontLookup(preferences)
    val chars = "a\u0436".toCharArray()
    assertThat(lookup.fontAbleToDisplay(chars, 0, 1, Font.BOLD, context))
      .isSameAs(ComplementaryFontsRegistry.getFontAbleToDisplay(chars, 0, 1, Font.BOLD, preferences, context))
    assertThat(lookup.fontAbleToDisplay("a\u0436", 1, 2, Font.ITALIC, context))
      .isSameAs(ComplementaryFontsRegistry.getFontAbleToDisplay("a\u0436", 1, 2, Font.ITALIC, preferences, context))
    assertThat(lookup.fontAbleToDisplay('a'.code, Font.PLAIN, context))
      .isSameAs(ComplementaryFontsRegistry.getFontAbleToDisplay('a'.code, Font.PLAIN, preferences, context))
  }
}
