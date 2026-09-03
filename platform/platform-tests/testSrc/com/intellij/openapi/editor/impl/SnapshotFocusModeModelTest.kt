// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.marker.PMarker
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.editor.impl.marker.UsePMarkerImplementation
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Segment
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@UsePMarkerImplementation
@RunInEdt(writeIntent = true)
class SnapshotFocusModeModelTest {
  @Test
  fun `focus region resolves in snapshot branches`() = withEditor("abcdef") { editor ->
    val document = editor.elfDocument as DocumentImpl
    val initialSnapshot = document.core.snapshot()
    val region = editor.focusModeModel.createFocusRegion(2, 4)
    val snapshotRegion = region as PMarker

    assertThat(region).isInstanceOf(SnapshotRangeMarkerImpl::class.java)
    assertThat(snapshotRegion.resolve(initialSnapshot))
      .extracting("startOffset", "endOffset").containsExactly(2, 4)

    val branch = initialSnapshot.applyOp(textPatch(0, 0, "x"))
    assertThat(snapshotRegion.resolve(branch))
      .extracting("startOffset", "endOffset").containsExactly(3, 5)
    assertThat(snapshotRegion.resolve(initialSnapshot))
      .extracting("startOffset", "endOffset").containsExactly(2, 4)
  }

  @Test
  fun `document edit updates focus region lookup`() = withEditor("abcdef") { editor ->
    val document = editor.elfDocument
    val model = editor.focusModeModel
    val region = model.createFocusRegion(2, 4)

    document.insertString(0, "x")

    assertThat(model.findFocusRegion(3, 5)).isSameAs(region)
    assertThat(model.findFocusRegion(2, 4)).isNull()
  }

  @Test
  fun `focus mode uses the innermost snapshot region`() = withEditor("aa\nbb\ncc\n") { editor ->
    val model = editor.focusModeModel
    model.createFocusRegion(0, 8)
    model.createFocusRegion(3, 5)

    val caret = editor.caretModel.primaryCaret
    caret.moveToOffset(4)
    model.applyFocusMode(caret)

    assertThat(model.focusModeRange)
      .extracting("startOffset", "endOffset").containsExactly(3, 6)
  }

  @Test
  fun `editors keep separate focus regions`() {
    val document = DocumentImpl("abcdef", true)
    withEditor(document) { firstEditor ->
      withEditor(document) { secondEditor ->
        val firstModel = firstEditor.focusModeModel
        val secondModel = secondEditor.focusModeModel
        val firstRegion = firstModel.createFocusRegion(1, 3)

        assertThat(firstModel.findFocusRegion(1, 3)).isSameAs(firstRegion)
        assertThat(secondModel.findFocusRegion(1, 3)).isNull()

        val secondRegion = secondModel.createFocusRegion(1, 3)
        assertThat(firstModel.findFocusRegion(1, 3)).isSameAs(firstRegion)
        assertThat(secondModel.findFocusRegion(1, 3)).isSameAs(secondRegion)
      }
    }
  }

  @Test
  fun `focus region removal sends one notification`() = withEditor("abcdef") { editor ->
    val model = editor.focusModeModel
    val disposable = Disposer.newDisposable()
    val added = mutableListOf<Segment>()
    val removed = mutableListOf<Segment>()
    try {
      model.addFocusSegmentListener(object : FocusModeModel.FocusModeModelListener {
        override fun focusRegionAdded(newRegion: Segment) {
          added.add(newRegion)
        }

        override fun focusRegionRemoved(oldRegion: Segment) {
          removed.add(oldRegion)
        }
      }, disposable)

      val region = model.createFocusRegion(2, 4)
      model.removeFocusRegion(region)
      model.removeFocusRegion(region)

      assertThat(added).containsExactly(region)
      assertThat(removed).containsExactly(region)
      assertThat(region.isValid).isFalse()
      assertThat(model.findFocusRegion(2, 4)).isNull()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  @UsePMarkerImplementation(false)
  fun `disabled snapshot marker implementation uses legacy focus region`() = withEditor("abcdef") { editor ->
    val region = editor.focusModeModel.createFocusRegion(2, 4)

    assertThat(region).isExactlyInstanceOf(RangeMarkerImpl::class.java)
  }

  private fun withEditor(text: String, action: (EditorImpl) -> Unit) {
    withEditor(DocumentImpl(text, true), action)
  }

  private fun withEditor(document: DocumentImpl, action: (EditorImpl) -> Unit) {
    val editorFactory = EditorFactory.getInstance()
    val editor = editorFactory.createEditor(document) as EditorImpl
    try {
      action(editor)
    }
    finally {
      editorFactory.releaseEditor(editor)
    }
  }

  private fun textPatch(startOffset: Int, endOffset: Int, newFragment: String): DocumentTextPatch {
    return DocumentTextPatch.simple(
      startOffset = startOffset,
      endOffset = endOffset,
      newFragment = newFragment,
      newModStamp = 1,
      clearLineFlags = false,
    )
  }
}
