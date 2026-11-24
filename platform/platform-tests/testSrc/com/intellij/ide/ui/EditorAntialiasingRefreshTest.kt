// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** Checks that a change of [UISettings.editorAAType] refreshes the open editors. `EditorFactoryImpl` follows the value. */
@TestApplication
class EditorAntialiasingRefreshTest {
  @BeforeEach
  @AfterEach
  fun dropStoredSettings(): Unit = timeoutRunBlocking(60.seconds) {
    dropStoredUISettings()
  }

  @Test
  fun `a change of the editor antialiasing refreshes the editors`(): Unit = timeoutRunBlocking(60.seconds) {
    val editorFactory = EditorFactory.getInstance()
    val editor = withContext(Dispatchers.EDT) { editorFactory.createEditor(editorFactory.createDocument("text")) }
    // A direct write of the stored options sends no event, so the editor shows the new value only after a refresh.
    val options = EditorSettingsExternalizable.getInstance().options
    val originalValue = options.IS_FOLDING_OUTLINE_SHOWN_ONLY_ON_HOVER
    try {
      // The first read takes a snapshot of the options, and the editor shows it until a refresh.
      assertEquals(originalValue, withContext(Dispatchers.EDT) { editor.settings.isFoldingOutlineShownOnlyOnHover })
      options.IS_FOLDING_OUTLINE_SHOWN_ONLY_ON_HOVER = !originalValue
      val settings = UISettings.getInstance()
      settings.editorAAType = if (settings.editorAAType == AntialiasingType.OFF) AntialiasingType.GREYSCALE else AntialiasingType.OFF

      waitUntil("the editor is refreshed") {
        withContext(Dispatchers.EDT) { editor.settings.isFoldingOutlineShownOnlyOnHover == !originalValue }
      }
    }
    finally {
      options.IS_FOLDING_OUTLINE_SHOWN_ONLY_ON_HOVER = originalValue
      withContext(Dispatchers.EDT) {
        editorFactory.refreshAllEditors()
        editorFactory.releaseEditor(editor)
      }
    }
  }
}
