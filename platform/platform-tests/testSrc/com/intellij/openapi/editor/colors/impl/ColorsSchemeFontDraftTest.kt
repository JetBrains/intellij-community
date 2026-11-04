// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ColorsSchemeFontDraftTest {
  @Test
  fun `a change of the editor font starts from a copy of the application font`() {
    val start = ColorsSchemeSnapshot.initial()
    val draft = ColorsSchemeFontDraft(start, appFont())
    draft.setLineSpacing(1.7f)
    val result = draft.snapshot()
    assertThat(result.useAppFontPreferencesInEditor()).isFalse()
    assertThat(result.fontPreferences().fontFamily).isEqualTo(APP_FAMILY)
    assertThat(result.fontPreferences().getSize2D(APP_FAMILY)).isEqualTo(14f)
    assertThat(result.fontPreferences().lineSpacing).isEqualTo(1.7f)
    assertThat(start.useAppFontPreferencesInEditor()).isTrue()
    assertThat(start.fontPreferences()).isSameAs(FontPreferencesSnapshot.EMPTY)
  }

  @Test
  fun `a change of the console font starts from a copy of the editor font`() {
    val draft = ColorsSchemeFontDraft(ColorsSchemeSnapshot.initial(), appFont())
    draft.setEditorFontSize(15f)
    draft.setConsoleLineSpacing(1.9f)
    val result = draft.snapshot()
    assertThat(result.useEditorFontPreferencesInConsole()).isFalse()
    assertThat(result.consoleFontPreferences().fontFamily).isEqualTo(APP_FAMILY)
    assertThat(result.consoleFontPreferences().getSize2D(APP_FAMILY)).isEqualTo(15f)
    assertThat(result.consoleFontPreferences().lineSpacing).isEqualTo(1.9f)
    assertThat(result.fontPreferences().lineSpacing).isEqualTo(1.3f)
  }

  @Test
  fun `a new editor font name keeps the font size and the ligatures`() {
    val draft = ColorsSchemeFontDraft(ColorsSchemeSnapshot.initial(), appFont())
    draft.setUseLigatures(true)
    draft.setEditorFontSize(16f)
    draft.setEditorFontName(OTHER_FAMILY)
    val result = draft.snapshot().fontPreferences()
    assertThat(result.effectiveFontFamilies).containsExactly(OTHER_FAMILY)
    assertThat(result.getSize2D(OTHER_FAMILY)).isEqualTo(16f)
    assertThat(result.useLigatures()).isTrue()
  }

  @Test
  fun `a new console font name takes the editor font size when the console font has no size`() {
    val draft = ColorsSchemeFontDraft(ColorsSchemeSnapshot.initial(), appFont())
    draft.setEditorFontSize(13f)
    draft.changeConsoleFont { console ->
      console.clear()
      console.addFontFamily(OTHER_FAMILY)
    }
    draft.setConsoleFontName(CONSOLE_FAMILY)
    val result = draft.snapshot().consoleFontPreferences()
    assertThat(result.effectiveFontFamilies).containsExactly(CONSOLE_FAMILY)
    assertThat(result.getSize2D(CONSOLE_FAMILY)).isEqualTo(13f)
  }

  @Test
  fun `a draft without changes gives the start snapshot`() {
    val draft = ColorsSchemeFontDraft(ColorsSchemeSnapshot.initial(), appFont())
    draft.setUseLigatures(false)
    val own = draft.snapshot()
    val again = ColorsSchemeFontDraft(own, appFont())
    again.setUseLigatures(false)
    again.setFontPreferences(own.fontPreferences())
    assertThat(again.snapshot()).isSameAs(own)
  }

  @Test
  fun `setFontPreferences copies the values into the own editor font`() {
    val source = FontPreferencesImpl()
    source.register(OTHER_FAMILY, 18f)
    source.lineSpacing = 1.6f
    source.setUseLigatures(true)
    val draft = ColorsSchemeFontDraft(ColorsSchemeSnapshot.initial(), appFont())
    draft.setFontPreferences(source)
    draft.setConsoleFontPreferences(source)
    val result = draft.snapshot()
    assertThat(result.useAppFontPreferencesInEditor()).isFalse()
    assertThat(result.fontPreferences()).isEqualTo(source.snapshot)
    assertThat(result.useEditorFontPreferencesInConsole()).isFalse()
    assertThat(result.consoleFontPreferences()).isEqualTo(source.snapshot)
  }

  /**
   * Returns the application font of the tests: one font family with its own size and line spacing.
   */
  private fun appFont(): FontPreferencesImpl {
    val preferences = FontPreferencesImpl()
    preferences.register(APP_FAMILY, 14f)
    preferences.lineSpacing = 1.3f
    return preferences
  }

  private companion object {
    const val APP_FAMILY = "ColorsSchemeFontDraftTest.appFamily"
    const val OTHER_FAMILY = "ColorsSchemeFontDraftTest.otherFamily"
    const val CONSOLE_FAMILY = "ColorsSchemeFontDraftTest.consoleFamily"
  }
}
