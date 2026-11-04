// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.editor.colors.DelegatingFontPreferences
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.ModifiableFontPreferences
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.Test

@TestApplication
internal class AbstractColorsSchemeFontsTest {
  @Test
  fun `the font preferences of a scheme stay the same object across changes`() {
    val scheme = schemeWithOwnFonts()
    val preferences = scheme.fontPreferences
    val consolePreferences = scheme.consoleFontPreferences
    scheme.setEditorFontSize(20f)
    scheme.setConsoleFontSize(22f)
    assertThat(scheme.fontPreferences).isSameAs(preferences)
    assertThat(scheme.consoleFontPreferences).isSameAs(consolePreferences)
    assertThat(preferences.getSize2D(preferences.fontFamily)).isEqualTo(20f)
    assertThat(consolePreferences.getSize2D(consolePreferences.fontFamily)).isEqualTo(22f)
  }

  @Test
  fun `a write through the font preferences changes the scheme and keeps the old snapshot`() {
    val scheme = schemeWithOwnFonts()
    val preferences = scheme.fontPreferences as ModifiableFontPreferences
    val consolePreferences = scheme.consoleFontPreferences as ModifiableFontPreferences
    val before = preferences.snapshot
    val consoleBefore = consolePreferences.snapshot
    preferences.setLineSpacing(1.7f)
    consolePreferences.setLineSpacing(1.9f)
    assertThat(scheme.lineSpacing).isEqualTo(1.7f)
    assertThat(scheme.consoleLineSpacing).isEqualTo(1.9f)
    assertThat(before.lineSpacing).isNotEqualTo(1.7f)
    assertThat(consoleBefore.lineSpacing).isNotEqualTo(1.9f)
  }

  @Test
  fun `a scheme that uses the application font gives a delegate`() {
    val scheme = schemeWithOwnFonts()
    scheme.setUseAppFontPreferencesInEditor()
    scheme.setUseEditorFontPreferencesInConsole()
    assertThat(scheme.fontPreferences).isInstanceOf(DelegatingFontPreferences::class.java)
    assertThat(scheme.consoleFontPreferences).isInstanceOf(DelegatingFontPreferences::class.java)
    assertThat(scheme.isUseAppFontPreferencesInEditor).isTrue()
    assertThat(scheme.isUseEditorFontPreferencesInConsole).isTrue()
  }

  @Test
  fun `a write through old font preferences does not change a scheme that uses the application font`() {
    val scheme = schemeWithOwnFonts()
    val oldPreferences = scheme.fontPreferences as ModifiableFontPreferences
    scheme.setUseAppFontPreferencesInEditor()
    oldPreferences.setLineSpacing(2.5f)
    val appPreferences = AppEditorFontOptions.getInstance().fontPreferences
    assertThat(scheme.isUseAppFontPreferencesInEditor).isTrue()
    assertThat(scheme.lineSpacing).isEqualTo(appPreferences.lineSpacing)
  }

  @Test
  fun `copyTo gives the setters the font preferences that the getters return`() {
    val source = schemeWithOwnFonts()
    val target = CapturingScheme()
    source.copyTo(target)
    assertThat(target.fontPreferencesOfSetter).isSameAs(source.fontPreferences)
    assertThat(target.consoleFontPreferencesOfSetter).isSameAs(source.consoleFontPreferences)
  }

  @Test
  fun `the saved console font size falls back to the editor font size`() {
    val scheme = schemeWithOwnFonts()
    scheme.name = "ConsoleFontSizeFallback"
    val consolePreferences = scheme.consoleFontPreferences as ModifiableFontPreferences
    consolePreferences.runBatchUpdate { draft ->
      draft.clear()
      draft.addFontFamily(CONSOLE_FAMILY)
    }
    assertThat(consoleFontSizeOption(scheme.writeScheme())).isNull()
    consolePreferences.register(CONSOLE_FAMILY, 20f)
    assertThat(consoleFontSizeOption(scheme.writeScheme())).isEqualTo("20")
  }

  @Test
  fun `a scheme that is not global gives its own console font`() {
    val scheme = EditorColorsSchemeImpl(null)
    scheme.setConsoleFontSize(29f)
    assertThat(scheme.isUseAppFontPreferencesInEditor).isTrue()
    assertThat(EditorColorsManager.getInstance().globalScheme).isNotSameAs(scheme)
    assertThat(scheme.getFont(EditorFontType.CONSOLE_PLAIN).size2D).isEqualTo(29f)
  }

  /**
   * Returns a scheme with its own editor font and its own console font.
   */
  private fun schemeWithOwnFonts(): EditorColorsSchemeImpl {
    val scheme = EditorColorsSchemeImpl(null)
    scheme.setEditorFontSize(13f)
    scheme.setConsoleFontSize(15f)
    return scheme
  }

  private fun consoleFontSizeOption(root: Element): String? {
    val option = root.getChildren("option").firstOrNull { it.getAttributeValue("name") == "CONSOLE_FONT_SIZE" }
    return option?.getAttributeValue("value")
  }

  /**
   * Keeps the font preferences that its setters get.
   */
  private class CapturingScheme : EditorColorsSchemeImpl(null) {
    var fontPreferencesOfSetter: FontPreferences? = null
      private set

    var consoleFontPreferencesOfSetter: FontPreferences? = null
      private set

    override fun setFontPreferences(preferences: FontPreferences) {
      fontPreferencesOfSetter = preferences
      super.setFontPreferences(preferences)
    }

    override fun setConsoleFontPreferences(preferences: FontPreferences) {
      consoleFontPreferencesOfSetter = preferences
      super.setConsoleFontPreferences(preferences)
    }
  }

  private companion object {
    const val CONSOLE_FAMILY = "AbstractColorsSchemeFontsTest.consoleFamily"
  }
}
