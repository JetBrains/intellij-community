// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.state.ObservableStateListener
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestApplication
internal class SettingsImplBatchTest {
  @Test
  fun `nested changes notify immediately and refresh once`(): Unit = withEditor { editor ->
    val counter = RefreshCounter(editor)
    val settings = editor.settings as SettingsImpl
    val notifications = mutableListOf<String>()
    settings.getState().addPropertyChangeListener(object : ObservableStateListener {
      override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
        notifications.add(event.propertyName)
      }
    })
    val lineNumbers = settings.isLineNumbersShown()
    val folding = settings.isFoldingOutlineShown()
    settings.runWithDeferredEditorRefresh {
      settings.setLineNumbersShown(!lineNumbers)
      assertThat(settings.isLineNumbersShown()).isEqualTo(!lineNumbers)
      assertThat(notifications).contains("myAreLineNumbersShown")
      settings.runWithDeferredEditorRefresh {
        settings.setFoldingOutlineShown(!folding)
      }
      assertThat(notifications).contains("myIsFoldingOutlineShown")
      assertThat(counter.refreshes).isZero()
    }
    assertThat(counter.refreshes).isEqualTo(1)
    settings.runWithDeferredEditorRefresh { settings.setLineNumbersShown(!lineNumbers) }
    assertThat(counter.refreshes).isEqualTo(1)
  }

  @Test
  fun `an exception flushes changes and restores normal refresh`(): Unit = withEditor { editor ->
    val counter = RefreshCounter(editor)
    val settings = editor.settings as SettingsImpl
    val original = settings.isLineNumbersShown()
    val failure = IllegalStateException("test failure")
    assertThat(assertThrows<IllegalStateException> {
      settings.runWithDeferredEditorRefresh {
        settings.setLineNumbersShown(!original)
        throw failure
      }
    }).isSameAs(failure)
    assertThat(settings.isLineNumbersShown()).isEqualTo(!original)
    assertThat(counter.refreshes).isEqualTo(1)
    settings.setLineNumbersShown(original)
    assertThat(counter.refreshes).isEqualTo(2)
  }

  private fun withEditor(action: (EditorEx) -> Unit): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val factory = EditorFactory.getInstance()
      val document = factory.createDocument("first line\nsecond line")
      val editor = factory.createEditor(document, ProjectManager.getInstance().defaultProject) as EditorEx
      try {
        action(editor)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
  }

  private class RefreshCounter(editor: EditorEx) {
    var refreshes = 0
      private set

    init {
      val delegate = LexerEditorHighlighter(PlainSyntaxHighlighter(), editor.colorsScheme)
      editor.highlighter = object : EditorHighlighter by delegate {
        override fun setColorScheme(scheme: EditorColorsScheme) {
          refreshes++
          delegate.setColorScheme(scheme)
        }
      }
      refreshes = 0
    }
  }
}
