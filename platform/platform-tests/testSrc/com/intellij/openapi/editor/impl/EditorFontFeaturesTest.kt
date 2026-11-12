// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.font.TextAttribute

@TestApplication
internal class EditorFontFeaturesTest {
  private val font = Font(Font.MONOSPACED, Font.PLAIN, 12)

  @Test
  fun `ligatures on add the ligatures attribute`() {
    val result = EditorFontFeatures.withLigatures(font, true)
    assertThat(result.attributes[TextAttribute.LIGATURES]).isEqualTo(TextAttribute.LIGATURES_ON)
  }

  @Test
  fun `ligatures off keep the same font`() {
    assertThat(EditorFontFeatures.withLigatures(font, false)).isSameAs(font)
  }

  @Test
  fun `character variants derive a new font`() {
    assertThat(EditorFontFeatures.withFeatures(font, false, setOf("zero"))).isNotSameAs(font)
  }

  @Test
  fun `no character variants keep the same font when the ligatures are off`() {
    assertThat(EditorFontFeatures.withFeatures(font, false, emptySet())).isSameAs(font)
  }

  @Test
  fun `createFont takes the ligatures from its parameter`() {
    val preferences = FontPreferencesImpl()
    preferences.register(FontPreferences.DEFAULT_FONT_NAME, 13f)
    preferences.setUseLigatures(false)
    val result = EditorFontFeatures.createFont(FontPreferences.DEFAULT_FONT_NAME, Font.PLAIN, 13f, preferences, true)
    assertThat(result.attributes[TextAttribute.LIGATURES]).isEqualTo(TextAttribute.LIGATURES_ON)
    assertThat(result.size2D).isEqualTo(13f)
  }
}
