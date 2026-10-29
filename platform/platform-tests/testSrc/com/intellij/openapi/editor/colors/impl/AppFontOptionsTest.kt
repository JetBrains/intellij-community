// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorFontCache
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.ModifiableFontPreferences
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.util.xmlb.XmlSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Font

@TestApplication
internal class AppFontOptionsTest {
  private val cache = CountingFontCache()

  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable) {
    ApplicationManager.getApplication().replaceService(EditorFontCache::class.java, cache, disposable)
  }

  @Test
  fun `a load of the same state does not reset the editor font cache`() {
    val options = AppEditorFontOptions()
    val state = AppEditorFontOptions.PersistentFontPreferences(options.fontPreferences)
    options.loadState(state)
    assertThat(cache.resets).isZero()
  }

  @Test
  fun `a change without a state load resets the editor font cache`() {
    val options = AppEditorFontOptions()
    toggleLigatures(options)
    assertThat(cache.resets).isEqualTo(1)
  }

  @Test
  fun `the console switch to the editor font resets the editor font cache`() {
    val options = AppConsoleFontOptions()
    options.isUseEditorFont = false
    cache.resets = 0
    options.isUseEditorFont = true
    assertThat(cache.resets).isEqualTo(1)
  }

  @Test
  fun `a change resets the editor font cache once after repeated state loads`() {
    val options = AppEditorFontOptions()
    val state = AppEditorFontOptions.PersistentFontPreferences(options.fontPreferences)
    repeat(3) {
      options.loadState(state)
    }
    cache.resets = 0
    toggleLigatures(options)
    assertThat(cache.resets).isEqualTo(1)
  }

  @Test
  fun `the modification count is stable without a state load`() {
    val options = AppEditorFontOptions()
    options.noStateLoaded()
    val count = options.stateModificationCount
    assertThat(options.stateModificationCount).isEqualTo(count)
  }

  @Test
  fun `the console modification count is stable after a load with the editor font`() {
    val state = AppConsoleFontOptions().state
    val options = AppConsoleFontOptions()
    options.loadState(state)
    val count = options.stateModificationCount
    assertThat(options.stateModificationCount).isEqualTo(count)
  }

  @Test
  fun `a custom console font stays after a save and a load`() {
    val options = AppConsoleFontOptions()
    options.isUseEditorFont = false
    val element = XmlSerializer.serialize(options.state)
    val loaded = AppConsoleFontOptions()
    loaded.loadState(XmlSerializer.deserialize(element, AppConsoleFontOptions.ConsoleFontState::class.java))
    assertThat(loaded.isUseEditorFont).isFalse()
  }

  private fun toggleLigatures(options: AppEditorFontOptions) {
    val preferences = options.fontPreferences as ModifiableFontPreferences
    preferences.setUseLigatures(!preferences.useLigatures())
  }

  private class CountingFontCache : EditorFontCache() {
    var resets = 0

    override fun getFont(key: EditorFontType?): Font {
      throw UnsupportedOperationException()
    }

    override fun reset() {
      resets++
    }
  }
}
