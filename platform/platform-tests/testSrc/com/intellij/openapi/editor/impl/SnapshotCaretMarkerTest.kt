// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.impl.marker.UsePMarkerImplementation
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@UsePMarkerImplementation
class SnapshotCaretMarkerTest {
  @Test
  fun `caret and selection follow document edits`(): Unit = timeoutRunBlocking {
    val state = withEditor("abcdef") { editor ->
      val caret = editor.caretModel.primaryCaret
      caret.moveToOffset(4)
      caret.setSelection(1, 5)

      editor.document.insertString(0, "xy")

      CaretState(
        usesSnapshotStorage = editor.caretModel.isUsingSnapshotMarkerStorage,
        offset = caret.offset,
        selectionStart = caret.selectionStart,
        selectionEnd = caret.selectionEnd,
      )
    }

    assertThat(state.usesSnapshotStorage).isTrue()
    assertThat(state.offset).isEqualTo(6)
    assertThat(state.selectionStart).isEqualTo(3)
    assertThat(state.selectionEnd).isEqualTo(7)
  }

  @Test
  fun `whitespace insertion moves the caret after inserted text`(): Unit = timeoutRunBlocking {
    val offset = withEditor("a b") { editor ->
      val caret = editor.caretModel.primaryCaret
      caret.moveToOffset(2)

      editor.document.insertString(2, "  ")

      caret.offset
    }

    assertThat(offset).isEqualTo(4)
  }

  @Test
  fun `whole replacement restores the logical caret position`(): Unit = timeoutRunBlocking {
    val position = withEditor("first\nsecond\nthird") { editor ->
      val caret = editor.caretModel.primaryCaret
      caret.moveToLogicalPosition(LogicalPosition(1, 3))

      editor.document.setText("prefix\nfirst\nsecond\nthird")

      caret.logicalPosition
    }

    assertThat(position).isEqualTo(LogicalPosition(2, 3))
  }

  @Test
  @UsePMarkerImplementation(false)
  fun `disabled snapshot marker implementation uses caret marker trees`(): Unit = timeoutRunBlocking {
    val usesSnapshotStorage = withEditor("abc") { editor ->
      editor.caretModel.isUsingSnapshotMarkerStorage
    }

    assertThat(usesSnapshotStorage).isFalse()
  }

  private suspend fun <T> withEditor(text: String, action: suspend (EditorImpl) -> T): T {
    return withContext(Dispatchers.EDT) {
      val editorFactory = EditorFactory.getInstance()
      val editor = editorFactory.createEditor(DocumentImpl(text, true)) as EditorImpl
      try {
        action(editor)
      }
      finally {
        editorFactory.releaseEditor(editor)
      }
    }
  }

  private data class CaretState(
    val usesSnapshotStorage: Boolean,
    val offset: Int,
    val selectionStart: Int,
    val selectionEnd: Int,
  )
}
