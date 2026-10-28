// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.impl.marker.PMarkerResolution
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.editor.impl.marker.UsePMarkerImplementation
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Suppress("SuspiciousPackagePrivateAccess")
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
  fun `whitespace insertion replaces the caret marker`(): Unit = timeoutRunBlocking {
    val (beforeRoot, afterRoot) = withEditor("a b") { editor ->
      val caret = editor.caretModel.primaryCaret
      caret.moveToOffset(2)
      val positionMarkerField = CaretImpl::class.java.getDeclaredField("myPositionMarker").apply { isAccessible = true }
      val beforeMarker = positionMarkerField.get(caret) as SnapshotRangeMarkerImpl
      val beforeRoot = beforeMarker.currentRootReference().get()

      editor.document.insertString(2, "  ")

      val afterMarker = positionMarkerField.get(caret) as SnapshotRangeMarkerImpl
      beforeRoot to afterMarker.currentRootReference().get()
    }

    val beforeMarker = beforeRoot.overlappingIterator(0, 5, 0).asSequence().single()
    val afterMarker = afterRoot.overlappingIterator(0, 5, 0).asSequence().single()
    assertThat(afterMarker.handle).isNotEqualTo(beforeMarker.handle)
    assertThat(PMarkerResolution.toTextRange(beforeRoot.resolve(beforeMarker.handle))).isEqualTo(TextRange(2, 2))
    assertThat(afterRoot.resolve(beforeMarker.handle)).isEqualTo(PMarkerResolution.ABSENT)
    assertThat(PMarkerResolution.toTextRange(afterRoot.resolve(afterMarker.handle))).isEqualTo(TextRange(4, 4))
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
  fun `edit after a virtual caret keeps its position`(): Unit = timeoutRunBlocking {
    val position = withEditor("abc\nccccc") { editor ->
      editor.isColumnMode = true
      val caret = editor.caretModel.primaryCaret
      caret.moveToVisualPosition(VisualPosition(0, 4))

      editor.document.replaceString(6, 8, "CC")

      caret.visualPosition
    }

    assertThat(position).isEqualTo(VisualPosition(0, 4))
  }

  @Test
  fun `edit before a virtual caret keeps its virtual column`(): Unit = timeoutRunBlocking {
    val position = withEditor("abc\nccccc") { editor ->
      editor.isColumnMode = true
      val caret = editor.caretModel.primaryCaret
      caret.moveToVisualPosition(VisualPosition(1, 7))

      editor.document.insertString(0, "x\n")

      caret.visualPosition
    }

    assertThat(position).isEqualTo(VisualPosition(2, 7))
  }

  @Test
  fun `successive whitespace insertions move only the affected caret`(): Unit = timeoutRunBlocking {
    val offsets = withEditor("a b\nc d") { editor ->
      val firstCaret = editor.caretModel.primaryCaret
      firstCaret.moveToOffset(2)
      val secondCaret = checkNotNull(editor.caretModel.addCaret(VisualPosition(1, 2)))

      editor.document.insertString(2, "  ")
      val firstOffsets = listOf(firstCaret.offset, secondCaret.offset)
      editor.document.insertString(8, "  ")

      firstOffsets + listOf(firstCaret.offset, secondCaret.offset)
    }

    assertThat(offsets).containsExactly(4, 8, 4, 10)
  }

  @Test
  fun `moving a line keeps the virtual caret column`(): Unit = timeoutRunBlocking {
    val position = withEditor("abc\nxyz\n") { editor ->
      editor.isColumnMode = true
      val caret = editor.caretModel.primaryCaret
      caret.moveToVisualPosition(VisualPosition(0, 5))

      editor.document.moveText(0, 4, 8)

      caret.visualPosition
    }

    assertThat(position).isEqualTo(VisualPosition(1, 5))
  }

  @Test
  fun `insertion at the selection end removes its virtual columns`(): Unit = timeoutRunBlocking {
    val position = withEditor("abc") { editor ->
      editor.isColumnMode = true
      val caret = editor.caretModel.primaryCaret
      caret.moveToVisualPosition(VisualPosition(0, 5))
      caret.setSelection(VisualPosition(0, 1), 1, VisualPosition(0, 5), 3)

      editor.document.insertString(3, "x")

      caret.selectionEndPosition
    }

    assertThat(position).isEqualTo(VisualPosition(0, 3))
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
