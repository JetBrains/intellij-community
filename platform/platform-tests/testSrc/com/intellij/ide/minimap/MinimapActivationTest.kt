// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.ide.minimap.settings.MinimapSettingsState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Guards the minimap *activation* path: given availability + the enabled setting + a supported file, the
 * [MinimapService] must actually attach a `MinimapPanel` to the editor (and must not when any precondition fails).
 * This catches the "minimap silently never appears" regression that the render/pure-logic tests cannot see.
 */
class MinimapActivationTest : BasePlatformTestCase() {
  private var originalState: MinimapSettingsState? = null

  override fun setUp() {
    super.setUp()
    originalState = MinimapSettings.getInstance().state.copy()
    setSettings(enabled = true)
  }

  override fun tearDown() {
    try {
      originalState?.let { MinimapSettings.getInstance().setState(it) }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  private fun setSettings(enabled: Boolean) {
    MinimapSettings.getInstance().setState(
      MinimapSettings.getInstance().state.copy(enabled = enabled, insideScrollbar = false)
    )
  }

  private fun openMainEditor(name: String, text: String): EditorImpl {
    val virtualFile = myFixture.configureByText(name, text).virtualFile
    val document = FileDocumentManager.getInstance().getDocument(virtualFile)!!
    val editor = EditorFactory.getInstance()
      .createEditor(document, project, virtualFile, false, EditorKind.MAIN_EDITOR) as EditorImpl
    Disposer.register(testRootDisposable) { EditorFactory.getInstance().releaseEditor(editor) }
    return editor
  }

  private fun openAndUpdate(name: String, text: String): EditorImpl {
    val editor = openMainEditor(name, text)
    MinimapService.getInstance().editorOpened(editor)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    return editor
  }

  fun testInstalledWhenAvailableEnabledAndSupported() {
    TestModeFlags.set(MinimapAvailability.FORCE_AVAILABLE, true, testRootDisposable)
    val editor = openAndUpdate("a.txt", "alpha\nbeta\ngamma\n")
    assertTrue(MinimapService.getInstance().isMinimapInstalled(editor))
  }

  fun testNotInstalledWhenUnavailable() {
    // FORCE_AVAILABLE is left unset, and tests do not run as PyCharm -> the availability gate must reject installation.
    val editor = openAndUpdate("a.txt", "alpha\nbeta\n")
    assertFalse(MinimapService.getInstance().isMinimapInstalled(editor))
  }

  fun testNotInstalledWhenDisabled() {
    TestModeFlags.set(MinimapAvailability.FORCE_AVAILABLE, true, testRootDisposable)
    setSettings(enabled = false)
    val editor = openAndUpdate("a.txt", "alpha\nbeta\n")
    assertFalse(MinimapService.getInstance().isMinimapInstalled(editor))
  }
}
