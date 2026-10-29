// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.EditorFontCache
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font
import java.util.function.Supplier

@TestApplication
internal class EditorFontCacheImplTest {
  @Test
  fun `the cache builds the fonts once without a reset`() {
    val counter = BuildCounter()
    counter.cache.getFont(EditorFontType.PLAIN)
    counter.cache.getFont(EditorFontType.BOLD)
    assertThat(counter.builds).isEqualTo(1)
  }

  @Test
  fun `a reset during a build makes the next call build again`() {
    val counter = BuildCounter()
    counter.resetDuringNextBuild = true
    counter.cache.getFont(EditorFontType.PLAIN)
    counter.cache.getFont(EditorFontType.PLAIN)
    assertThat(counter.builds).isEqualTo(2)
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
   * Counts the font builds of [cache], because each build starts with a call of this supplier.
   * A reset in this call acts like a font change on another thread during the build.
   */
  private class BuildCounter : Supplier<EditorColorsScheme> {
    val cache = EditorFontCacheImpl(this)
    var builds = 0
    var resetDuringNextBuild = false

    override fun get(): EditorColorsScheme {
      builds++
      if (resetDuringNextBuild) {
        resetDuringNextBuild = false
        cache.reset()
      }
      return EditorColorsManager.getInstance().globalScheme
    }
  }
}
