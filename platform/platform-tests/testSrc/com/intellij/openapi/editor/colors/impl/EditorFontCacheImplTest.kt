// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class EditorFontCacheImplTest {
  @Test
  fun `the cache builds the fonts once without a reset`() {
    val cache = CountingFontCache()
    cache.getFont(EditorFontType.PLAIN)
    cache.getFont(EditorFontType.BOLD)
    assertThat(cache.builds).isEqualTo(1)
  }

  @Test
  fun `a reset during a build makes the next call build again`() {
    val cache = CountingFontCache()
    cache.resetDuringNextBuild = true
    cache.getFont(EditorFontType.PLAIN)
    cache.getFont(EditorFontType.PLAIN)
    assertThat(cache.builds).isEqualTo(2)
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
   * Counts the font builds, because each build starts with [getFontCacheScheme].
   * A reset in this call acts like a font change on another thread during the build.
   */
  private class CountingFontCache : EditorFontCacheImpl() {
    var builds = 0
    var resetDuringNextBuild = false

    override fun getFontCacheScheme(): EditorColorsScheme {
      builds++
      if (resetDuringNextBuild) {
        resetDuringNextBuild = false
        reset()
      }
      return super.getFontCacheScheme()
    }
  }
}
