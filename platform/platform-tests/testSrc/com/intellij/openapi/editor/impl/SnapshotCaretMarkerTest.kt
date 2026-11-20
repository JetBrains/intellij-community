// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.impl.marker.PMarkerRelocationRoot
import com.intellij.openapi.editor.impl.marker.PMarkerResolution
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.UsePMarkerImplementation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Suppress("SuspiciousPackagePrivateAccess")
@TestApplication
@UsePMarkerImplementation
class SnapshotCaretMarkerTest {
  @Test
  fun `typed actions publish caret relocations once per batch`(): Unit = timeoutRunBlocking {
    val count = 32
    val result = withEditor("  \n".repeat(count)) { editor ->
      editor.caretModel.primaryCaret.moveToOffset(1)
      for (line in 1 until count) editor.caretModel.addCaret(VisualPosition(line, 1))
      val marker = positionMarker(editor.caretModel.primaryCaret)
      val before = rootVersion(marker.currentRootReference().get())
      TypedAction.getInstance().actionPerformed(editor, 'a', DataContext.EMPTY_CONTEXT)
      Triple(rootVersion(marker.currentRootReference().get()) - before, editor.document.text, editor.caretModel.allCarets.map { it.offset })
    }
    assertThat(result.first).isBetween(1L, 3L)
    assertThat(result.second).isEqualTo(" a \n".repeat(count))
    assertThat(result.third).isEqualTo(List(count) { it * 4 + 2 })
  }

  @Test
  fun `an interrupted batch publishes its moves and allows another batch`(): Unit = timeoutRunBlocking {
    val result = withEditor("abcdef") { editor ->
      val caret = editor.caretModel.primaryCaret
      val interrupted = try {
        editor.caretModel.runBatchCaretOperation {
          caret.moveToOffset(3)
          throw BatchAbort()
        }
        false
      }
      catch (_: BatchAbort) {
        true
      }
      caret.moveToOffset(4)
      Triple(interrupted, caret.offset, positionMarker(caret).currentRootReference().get() is PMarkerRelocationRoot)
    }
    assertThat(result.first).isTrue()
    assertThat(result.second).isEqualTo(4)
    assertThat(result.third).isFalse()
  }

  private class BatchAbort : RuntimeException()

  private fun rootVersion(root: PMarkerRoot): Long {
    val version = root.javaClass.getDeclaredField("version").apply { isAccessible = true }.get(root)
    return version.javaClass.getDeclaredField("number").apply { isAccessible = true }.getLong(version)
  }

  @Test
  fun `caret batches retain intermediate positions and reuse marker handles`(): Unit = timeoutRunBlocking {
    val result = withEditor("abcdef") { editor ->
      val caret = editor.caretModel.primaryCaret
      val marker = positionMarker(caret)
      val original = marker.currentRootReference().get()
      val roots = ArrayList<PMarkerRoot>()
      editor.caretModel.runBatchCaretOperation {
        caret.moveToOffset(2)
        roots.add(marker.currentRootReference().get())
        caret.moveToOffset(4)
        roots.add(marker.currentRootReference().get())
      }
      val allRoots = buildList {
        add(original)
        addAll(roots)
        add(marker.currentRootReference().get())
      }
      allRoots to (positionMarker(caret) === marker)
    }
    assertThat(result.second).isTrue()
    val offsets = result.first.map { root ->
      root.overlappingIterator(0, 6, 0).asSequence().single().nodeStart
    }
    assertThat(offsets).containsExactly(0, 2, 4, 4)
    assertThat(result.first.last()).isNotInstanceOf(PMarkerRelocationRoot::class.java)
  }

  @Test
  fun `typing across a caret batch keeps snapshots consistent with caret reads`(): Unit = timeoutRunBlocking {
    val result = withEditor("  \n  \n  ") { editor ->
      val first = editor.caretModel.primaryCaret
      first.moveToOffset(1)
      editor.caretModel.addCaret(VisualPosition(1, 1))
      editor.caretModel.addCaret(VisualPosition(2, 1))
      val carets = editor.caretModel.allCarets
      val markers = carets.map { positionMarker(it) }
      val roots = ArrayList<Pair<PMarkerRoot, List<Int>>>()
      editor.caretModel.runBatchCaretOperation {
        for (caret in carets.asReversed()) {
          val offset = caret.offset
          editor.document.insertString(offset, "a")
          caret.moveToOffset(offset + 1)
          roots.add(markers.first().currentRootReference().get() to carets.map { it.offset })
        }
      }
      Triple(editor.document.text, roots, markers)
    }
    assertThat(result.first).isEqualTo(" a \n a \n a ")
    for ((root, offsets) in result.second) {
      assertThat(result.third.map { PMarkerResolution.startOffset(root.resolve(it.markerHandle)) }).isEqualTo(offsets)
    }
    assertThat(result.second.last().second).containsExactly(2, 6, 10)
  }

  private fun positionMarker(caret: Caret): SnapshotRangeMarkerImpl =
    CaretImpl::class.java.getDeclaredField("myPositionMarker").apply { isAccessible = true }.get(caret) as SnapshotRangeMarkerImpl

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
