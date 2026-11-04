// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.EditorFontCache
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.ModifiableFontPreferences
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font
import java.util.function.Supplier

@TestApplication
internal class EditorFontCacheImplTest {
  @Test
  fun `the cache builds the fonts once without a change`() {
    val cache = EditorFontCacheImpl { EditorColorsManager.getInstance().globalScheme }
    val plain = cache.getFont(EditorFontType.PLAIN)
    assertThat(cache.getFont(EditorFontType.PLAIN)).isSameAs(plain)
  }

  @Test
  fun `a reset during a build makes the next call build again`() {
    val supplier = ResettingSupplier()
    supplier.resetDuringNextCall = true
    val first = supplier.cache.getFont(EditorFontType.PLAIN)
    val second = supplier.cache.getFont(EditorFontType.PLAIN)
    assertThat(second).isNotSameAs(first)
    assertThat(supplier.cache.getFont(EditorFontType.PLAIN)).isSameAs(second)
  }

  @Test
  fun `a change of the font preferences in place gives new fonts`() {
    val scheme = EditorColorsSchemeImpl(null)
    scheme.setEditorFontSize(13f)
    scheme.getFont(EditorFontType.PLAIN)
    val preferences = scheme.fontPreferences as ModifiableFontPreferences
    preferences.register(preferences.fontFamily, 17f)
    assertThat(scheme.getFont(EditorFontType.PLAIN).size2D).isEqualTo(17f)
  }

  @Test
  fun `a console font change keeps the editor fonts`() {
    val scheme = EditorColorsSchemeImpl(null)
    scheme.setEditorFontSize(13f)
    scheme.setConsoleFontSize(15f)
    val plain = scheme.getFont(EditorFontType.PLAIN)
    scheme.setConsoleFontSize(17f)
    assertThat(scheme.getFont(EditorFontType.PLAIN)).isSameAs(plain)
    assertThat(scheme.getFont(EditorFontType.CONSOLE_PLAIN).size2D).isEqualTo(17f)
  }

  @Test
  fun `a change of the application console font reaches the console fonts of a scheme`() {
    val scheme = EditorColorsSchemeImpl(null)
    scheme.setEditorFontSize(13f)
    val consoleFontOptions = AppConsoleFontOptions.getInstance()
    val useEditorFont = consoleFontOptions.isUseEditorFont
    consoleFontOptions.isUseEditorFont = false
    try {
      val appConsoleFont = consoleFontOptions.fontPreferences as ModifiableFontPreferences
      val appConsoleFontBefore = appConsoleFont.snapshot
      try {
        scheme.getFont(EditorFontType.CONSOLE_PLAIN)
        appConsoleFont.register(appConsoleFont.fontFamily, 23f)
        assertThat(scheme.getFont(EditorFontType.CONSOLE_PLAIN).size2D).isEqualTo(23f)
      } finally {
        appConsoleFontBefore.copyTo(appConsoleFont)
      }
    } finally {
      consoleFontOptions.isUseEditorFont = useEditorFont
    }
  }

  @Test
  fun `the application cache holds the fonts of the global scheme`() {
    val cache = EditorFontCache.getInstance()
    cache.reset()
    val globalScheme = EditorColorsManager.getInstance().globalScheme
    assertThat(cache).isInstanceOf(GlobalSchemeFontCache::class.java)
    assertThat(cache.getFont(EditorFontType.PLAIN).size2D).isEqualTo(globalScheme.editorFontSize2D)
  }

  @Test
  fun `a font change of a scheme resets the font cache of the scheme`() {
    val scheme = EditorColorsSchemeImpl(null)
    scheme.setEditorFontSize(13f)
    scheme.getFont(EditorFontType.PLAIN)
    scheme.setEditorFontSize(15f)
    assertThat(scheme.getFont(EditorFontType.PLAIN).size2D).isEqualTo(15f)
  }

  @Test
  fun `a console font name change of a scheme resets the console fonts`() {
    val scheme = EditorColorsSchemeImpl(null)
    scheme.setEditorFontSize(13f)
    scheme.setConsoleFontSize(20f)
    val before = scheme.getFont(EditorFontType.CONSOLE_PLAIN)
    scheme.setConsoleFontName(Font.MONOSPACED)
    assertThat(scheme.getFont(EditorFontType.CONSOLE_PLAIN).family).isNotEqualTo(before.family)
  }

  @Test
  fun `presentation mode gives the same font on each call`() {
    val cache = EditorFontCacheImpl { EditorColorsManager.getInstance().globalScheme }
    val uiSettings = UISettings.getInstance()
    val initialPresentationMode = uiSettings.presentationMode
    try {
      uiSettings.presentationMode = true
      val font = cache.getFont(EditorFontType.PLAIN)
      assertThat(font.size2D).isEqualTo(UISettingsUtils.with(uiSettings).presentationModeFontSize)
      assertThat(cache.getFont(EditorFontType.PLAIN)).isSameAs(font)
    } finally {
      uiSettings.presentationMode = initialPresentationMode
    }
  }

  @Test
  fun `the console size stays after a build in presentation mode`() {
    val scheme = EditorColorsSchemeImpl(null)
    scheme.setEditorFontSize(13f)
    scheme.setConsoleFontSize(20f)
    val uiSettings = UISettings.getInstance()
    val initialPresentationMode = uiSettings.presentationMode
    try {
      uiSettings.presentationMode = true
      scheme.getFont(EditorFontType.CONSOLE_PLAIN)
      uiSettings.presentationMode = false
      assertThat(scheme.getFont(EditorFontType.CONSOLE_PLAIN).size2D).isEqualTo(20f)
    } finally {
      uiSettings.presentationMode = initialPresentationMode
    }
  }

  /**
   * Gives the global scheme to [cache], and resets the cache in the next call when [resetDuringNextCall] is set.
   * The cache calls the supplier after it reads its fonts, so the reset acts like a reset on another thread during a build.
   */
  private class ResettingSupplier : Supplier<EditorColorsScheme> {
    val cache = EditorFontCacheImpl(this)
    var resetDuringNextCall = false

    override fun get(): EditorColorsScheme {
      if (resetDuringNextCall) {
        resetDuringNextCall = false
        cache.reset()
      }
      return EditorColorsManager.getInstance().globalScheme
    }
  }
}
