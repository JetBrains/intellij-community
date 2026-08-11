// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.DocumentUtil
import javax.swing.BorderFactory
import kotlin.test.assertFailsWith

class EditorMappingTest : AbstractEditorTest() {
  fun `test yToVisualLine with top inset`() {
    initText("foo\nbar")
    editor.contentComponent.border = BorderFactory.createEmptyBorder(5, 0, 0, 0)
    val y = editor.visualLineToY(1)
    assertEquals(1, editor.yToVisualLine(y))
  }

  fun `test visualLineStartOffset clamps non-positive and past-last visual lines`() {
    initText("abc\ndef")

    assertEquals(0, editorImpl.visualLineStartOffset(-1))
    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(editor.document.textLength, editorImpl.visualLineStartOffset(editorImpl.visibleLineCount))
    assertEquals(editor.document.textLength, editorImpl.visualLineStartOffset(editorImpl.visibleLineCount + 1))
  }

  fun `test visualLineStartOffset returns zero for empty document`() {
    initText("")

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(0, editorImpl.visualLineStartOffset(1))
  }

  fun `test visualLineStartOffset is not allowed during bulk document update`() {
    initText("abc")

    assertFailsWith<RuntimeException> {
      DocumentUtil.executeInBulk(editor.document as DocumentEx) {
        editorImpl.visualLineStartOffset(0)
      }
    }
  }

  fun `test visualLineStartOffset is not allowed during batch inlay update`() {
    initText("abc")

    assertFailsWith<IllegalStateException> {
      editor.inlayModel.execute(true) {
        editorImpl.visualLineStartOffset(0)
      }
    }
  }

  fun `test offsetToVisualLine returns zero for negative offsets and empty document`() {
    initText("abc")

    assertEquals(0, editorImpl.offsetToVisualLine(-1, false))
    assertEquals(0, editorImpl.offsetToVisualLine(-1, true))

    initText("")

    assertEquals(0, editorImpl.offsetToVisualLine(0, false))
    assertEquals(0, editorImpl.offsetToVisualLine(1, true))
  }

  fun `test offsetToVisualLine is not allowed during bulk document update`() {
    initText("abc")

    assertFailsWith<RuntimeException> {
      DocumentUtil.executeInBulk(editor.document as DocumentEx) {
        editorImpl.offsetToVisualLine(0, false)
      }
    }
  }

  fun `test offsetToVisualLine is not allowed during batch inlay update`() {
    initText("abc")

    assertFailsWith<IllegalStateException> {
      editor.inlayModel.execute(true) {
        editorImpl.offsetToVisualLine(0, false)
      }
    }
  }

  fun `test offsetToVisualLine clamps offsets past document end`() {
    initText("abc\ndef")

    assertEquals(1, editorImpl.offsetToVisualLine(editor.document.textLength, false))
    assertEquals(1, editorImpl.offsetToVisualLine(editor.document.textLength + 10, false))
    assertEquals(1, editorImpl.offsetToVisualLine(editor.document.textLength + 10, true))
  }

  fun `test offsetToVisualLine returns logical lines without wraps or folds`() {
    initText("abc\ndef\nghi")

    assertEquals(0, editorImpl.offsetToVisualLine(0, false))
    assertEquals(0, editorImpl.offsetToVisualLine(3, false))
    assertEquals(1, editorImpl.offsetToVisualLine(4, false))
    assertEquals(1, editorImpl.offsetToVisualLine(7, true))
    assertEquals(2, editorImpl.offsetToVisualLine(8, false))
  }

  fun `test offsetToVisualLine returns logical lines when soft wraps are enabled but none are registered`() {
    initText("abc\ndef")
    configureSoftWraps(100)
    verifySoftWrapPositions()

    assertEquals(0, editorImpl.offsetToVisualLine(0, false))
    assertEquals(1, editorImpl.offsetToVisualLine(4, false))
  }

  fun `test offsetToVisualLine ignores registered soft wraps when soft wrapping is disabled`() {
    initText("abcdefghi\nxyz")
    configureSoftWraps(4)
    verifySoftWrapPositions(4, 7)

    editor.settings.setUseSoftWraps(false)

    assertEquals(0, editorImpl.offsetToVisualLine(4, false))
    assertEquals(0, editorImpl.offsetToVisualLine(8, false))
    assertEquals(1, editorImpl.offsetToVisualLine(10, false))
  }

  fun `test offsetToVisualLine distinguishes offsets before and after exact soft wrap`() {
    initText("abcdefghi")
    configureSoftWraps(4)
    verifySoftWrapPositions(4, 7)

    assertEquals(0, editorImpl.offsetToVisualLine(4, true))
    assertEquals(1, editorImpl.offsetToVisualLine(4, false))
    assertEquals(1, editorImpl.offsetToVisualLine(5, false))
    assertEquals(1, editorImpl.offsetToVisualLine(7, true))
    assertEquals(2, editorImpl.offsetToVisualLine(7, false))
    assertEquals(2, editorImpl.offsetToVisualLine(8, false))
  }

  fun `test offsetToVisualLine with legacy soft wrap implementation`() {
    doTestOffsetToVisualLineWithSoftWrapImplementation(useNewSoftWraps = false)
  }

  fun `test offsetToVisualLine with experimental soft wrap implementation`() {
    doTestOffsetToVisualLineWithSoftWrapImplementation(useNewSoftWraps = true)
  }

  fun `test offsetToVisualLine with experimental custom soft wrap`() {
    setUpCustomWrapSupport()
    initText("abcdefghi")

    assertNotNull(editor.customWrapModel.runBatchMutation { addWrap(4) })

    assertEquals(0, editorImpl.offsetToVisualLine(4, true))
    assertEquals(1, editorImpl.offsetToVisualLine(4, false))
    assertEquals(1, editorImpl.offsetToVisualLine(5, false))
  }

  fun `test offsetToVisualLine aligns offset inside surrogate pair`() {
    initText("a\n${SURROGATE_PAIR}bc")

    assertEquals(editorImpl.offsetToVisualLine(2, false), editorImpl.offsetToVisualLine(3, false))
  }

  fun `test offsetToVisualLine maps collapsed fold start and interior to the same visual line`() {
    initText("aaa\nbbbb\ncccc\ndddd\neeee")
    val foldStartOffset = editor.document.getLineStartOffset(1)
    val foldEndOffset = editor.document.getLineEndOffset(3)
    val afterFoldOffset = editor.document.getLineStartOffset(4)
    addCollapsedFoldRegion(foldStartOffset, foldEndOffset, "...")

    assertEquals(1, editorImpl.offsetToVisualLine(foldStartOffset, false))
    assertEquals(1, editorImpl.offsetToVisualLine(editor.document.getLineStartOffset(2), false))
    assertEquals(1, editorImpl.offsetToVisualLine(foldEndOffset - 1, true))
    assertEquals(2, editorImpl.offsetToVisualLine(afterFoldOffset, false))
  }

  fun `test offsetToVisualLine returns zero inside whole-document collapsed fold`() {
    initText("aaa\nbbbb\ncccc")
    addCollapsedFoldRegion(0, editor.document.textLength, "...")

    assertEquals(0, editorImpl.offsetToVisualLine(0, false))
    assertEquals(0, editorImpl.offsetToVisualLine(editor.document.getLineStartOffset(1), false))
    assertEquals(0, editorImpl.offsetToVisualLine(editor.document.textLength, false))
  }

  fun `test offsetToVisualLine ignores expanded fold region`() {
    initText("aaa\nbbbb\ncccc")
    val foldStartOffset = editor.document.getLineStartOffset(1)
    val foldEndOffset = editor.document.getLineEndOffset(2)
    val foldRegion = addFoldRegion(foldStartOffset, foldEndOffset, "...")

    assertNotNull(foldRegion)
    assertTrue(foldRegion.isExpanded)
    assertEquals(1, editorImpl.offsetToVisualLine(foldStartOffset, false))
    assertEquals(2, editorImpl.offsetToVisualLine(editor.document.getLineStartOffset(2), false))
  }

  fun `test offsetToVisualLine uses outer collapsed fold for nested folds`() {
    initText("aaa\nbbbb\ncccc\ndddd\neeee")
    val outerStartOffset = editor.document.getLineStartOffset(1)
    val outerEndOffset = editor.document.getLineEndOffset(3)
    val afterOuterFoldOffset = editor.document.getLineStartOffset(4)
    val innerStartOffset = editor.document.getLineStartOffset(2)
    val innerEndOffset = editor.document.getLineStartOffset(3)
    val outerFoldRegion = addFoldRegion(outerStartOffset, outerEndOffset, "outer")
    val innerFoldRegion = addFoldRegion(innerStartOffset, innerEndOffset, "inner")

    assertNotNull(outerFoldRegion)
    assertNotNull(innerFoldRegion)
    runFoldingOperation {
      innerFoldRegion!!.isExpanded = false
      outerFoldRegion!!.isExpanded = false
    }

    assertEquals(1, editorImpl.offsetToVisualLine(innerStartOffset, false))
    assertEquals(2, editorImpl.offsetToVisualLine(afterOuterFoldOffset, false))
  }

  fun `test visualLineStartOffset returns document line starts without wraps or folds`() {
    initText("abc\ndefghi")

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(4, editorImpl.visualLineStartOffset(1))
  }

  fun `test visualLineStartOffset returns start of trailing empty line`() {
    initText("abc\n")

    assertEquals(editor.document.textLength, editorImpl.visualLineStartOffset(1))
  }

  fun `test visualLineStartOffset returns document line starts when soft wraps are enabled but none are registered`() {
    initText("abc\ndef")
    configureSoftWraps(100)
    verifySoftWrapPositions()

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(4, editorImpl.visualLineStartOffset(1))
  }

  fun `test visualLineStartOffset ignores soft wraps when soft wrapping is disabled`() {
    initText("abcdefghi\nxyz")
    configureSoftWraps(4)
    verifySoftWrapPositions(4, 7)

    editor.settings.setUseSoftWraps(false)

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(10, editorImpl.visualLineStartOffset(1))
    assertEquals(editor.document.textLength, editorImpl.visualLineStartOffset(2))
  }

  fun `test visualLineStartOffset returns folded region start when binary search lands inside collapsed fold`() {
    initText("aaa\nbbbb\ncccc\ndddd\neeee")
    val foldStartOffset = editor.document.getLineStartOffset(1)
    val foldEndOffset = editor.document.getLineStartOffset(4)
    addCollapsedFoldRegion(foldStartOffset, foldEndOffset, "...")

    assertEquals(foldStartOffset, editorImpl.visualLineStartOffset(1))
  }

  fun `test visualLineStartOffset maps collapsed fold that does not include following line break`() {
    initText("line 0\nline 1\nimport a\nimport b\nimport c\nclass C\nline 6")
    val foldStartOffset = editor.document.getLineStartOffset(2)
    val foldEndOffset = editor.document.getLineEndOffset(4)
    addCollapsedFoldRegion(foldStartOffset, foldEndOffset, "...")

    assertVisualLineStartOffsets(
      editor.document.getLineStartOffset(0),
      editor.document.getLineStartOffset(1),
      foldStartOffset,
      editor.document.getLineStartOffset(5),
      editor.document.getLineStartOffset(6),
      editor.document.textLength,
    )
  }

  fun `test visualLineStartOffset maps collapsed fold that ends at following line start`() {
    initText("line 0\nline 1\nline 2\nline 3\nline 4")
    val foldStartOffset = editor.document.getLineStartOffset(1)
    val foldEndOffset = editor.document.getLineStartOffset(3)
    addCollapsedFoldRegion(foldStartOffset, foldEndOffset, "...")

    assertEquals(1, editorImpl.offsetToVisualLine(foldStartOffset, false))
    assertEquals(1, editorImpl.offsetToVisualLine(foldEndOffset, false))
    assertVisualLineStartOffsets(
      editor.document.getLineStartOffset(0),
      foldStartOffset,
      editor.document.getLineStartOffset(4),
      editor.document.textLength,
    )
  }

  fun `test visualLineStartOffset handles adjacent collapsed folds with the same visual start`() {
    initText("line 0\nline 1\nline 2\nline 3\nline 4\nline 5\nline 6")
    val firstFoldStartOffset = editor.document.getLineStartOffset(1)
    val firstFoldEndOffset = editor.document.getLineStartOffset(3)
    val secondFoldEndOffset = editor.document.getLineStartOffset(5)
    addCollapsedFoldRegion(firstFoldStartOffset, firstFoldEndOffset, "...")
    addCollapsedFoldRegion(firstFoldEndOffset, secondFoldEndOffset, "...")

    assertEquals(1, editorImpl.offsetToVisualLine(firstFoldStartOffset, false))
    assertEquals(1, editorImpl.offsetToVisualLine(firstFoldEndOffset, false))
    assertEquals(1, editorImpl.offsetToVisualLine(secondFoldEndOffset, false))
    assertVisualLineStartOffsets(
      editor.document.getLineStartOffset(0),
      firstFoldStartOffset,
      editor.document.getLineStartOffset(6),
      editor.document.textLength,
    )
  }

  fun `test visualLineStartOffset maps mid-line collapsed fold to containing line start`() {
    initText("header\nbefore hidden starts\nstill hidden after\nnext")
    val foldStartOffset = editor.document.getLineStartOffset(1) + "before ".length
    val foldEndOffset = editor.document.getLineEndOffset(2)
    addCollapsedFoldRegion(foldStartOffset, foldEndOffset, "...")

    assertVisualLineStartOffsets(
      editor.document.getLineStartOffset(0),
      editor.document.getLineStartOffset(1),
      editor.document.getLineStartOffset(3),
      editor.document.textLength,
    )
  }

  fun `test visualLineStartOffset maps custom fold region`() {
    initText("line 0\nline 1\nline 2\nline 3\nline 4")
    assertNotNull(addCustomFoldRegion(1, 2, 30))

    assertVisualLineStartOffsets(
      editor.document.getLineStartOffset(0),
      editor.document.getLineStartOffset(1),
      editor.document.getLineStartOffset(3),
      editor.document.getLineStartOffset(4),
      editor.document.textLength,
    )
  }

  fun `test visualLineStartOffset ignores collapsed folds when folding is disabled`() {
    initText("line 0\nline 1\nline 2")
    addCollapsedFoldRegion(editor.document.getLineStartOffset(1), editor.document.getLineEndOffset(2), "...")
    val foldingModel = editor.foldingModel as FoldingModelEx

    try {
      foldingModel.setFoldingEnabled(false)

      assertVisualLineStartOffsets(
        editor.document.getLineStartOffset(0),
        editor.document.getLineStartOffset(1),
        editor.document.getLineStartOffset(2),
        editor.document.textLength,
      )
    }
    finally {
      foldingModel.setFoldingEnabled(true)
    }
  }

  fun `test visualLineStartOffset ignores single-line collapsed folds`() {
    initText("line 0\nline 1\nline 2")
    addCollapsedFoldRegion(editor.document.getLineStartOffset(1), editor.document.getLineEndOffset(1), "...")

    assertVisualLineStartOffsets(
      editor.document.getLineStartOffset(0),
      editor.document.getLineStartOffset(1),
      editor.document.getLineStartOffset(2),
      editor.document.textLength,
    )
  }

  fun `test visualLineStartOffset returns whole-document collapsed fold boundaries`() {
    initText("aaa\nbbbb\ncccc")
    addCollapsedFoldRegion(0, editor.document.textLength, "...")

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(editor.document.textLength, editorImpl.visualLineStartOffset(1))
  }

  fun `test visualLineStartOffset ignores expanded fold region`() {
    initText("aaa\nbbbb\ncccc")
    val foldStartOffset = editor.document.getLineStartOffset(1)
    val foldEndOffset = editor.document.getLineEndOffset(2)
    val foldRegion = addFoldRegion(foldStartOffset, foldEndOffset, "...")

    assertNotNull(foldRegion)
    assertTrue(foldRegion.isExpanded)
    assertEquals(foldStartOffset, editorImpl.visualLineStartOffset(1))
    assertEquals(editor.document.getLineStartOffset(2), editorImpl.visualLineStartOffset(2))
  }

  fun `test visualLineStartOffset uses outer collapsed fold start for nested folds`() {
    initText("aaa\nbbbb\ncccc\ndddd\neeee")
    val outerStartOffset = editor.document.getLineStartOffset(1)
    val outerEndOffset = editor.document.getLineStartOffset(4)
    val innerStartOffset = editor.document.getLineStartOffset(2)
    val innerEndOffset = editor.document.getLineStartOffset(3)
    val outerFoldRegion = addFoldRegion(outerStartOffset, outerEndOffset, "outer")
    val innerFoldRegion = addFoldRegion(innerStartOffset, innerEndOffset, "inner")

    assertNotNull(outerFoldRegion)
    assertNotNull(innerFoldRegion)
    runFoldingOperation {
      innerFoldRegion!!.isExpanded = false
      outerFoldRegion!!.isExpanded = false
    }

    assertEquals(outerStartOffset, editorImpl.visualLineStartOffset(1))
  }

  fun `test visualLineStartOffset returns exact soft wrap offset`() {
    initText("abcdefghi")
    configureSoftWraps(4)
    verifySoftWrapPositions(4, 7)

    assertEquals(4, editorImpl.visualLineStartOffset(1))
    assertEquals(7, editorImpl.visualLineStartOffset(2))
  }

  fun `test visualLineStartOffset returns previous soft wrap offset`() {
    initText("abcdefgh")
    configureSoftWraps(4)
    verifySoftWrapPositions(4)

    assertEquals(4, editorImpl.visualLineStartOffset(1))
  }

  fun `test visualLineStartOffset keeps line start when soft wrap starts at same offset`() {
    initText("abc\ndef")
    addCollapsedFoldRegion(3, 4, "...")
    configureSoftWraps(6)
    verifySoftWrapPositions(4)

    assertEquals(4, editorImpl.visualLineStartOffset(1))
  }

  fun `test visualLineStartOffset with legacy soft wrap implementation`() {
    doTestVisualLineStartOffsetWithSoftWrapImplementation(useNewSoftWraps = false)
  }

  fun `test visualLineStartOffset with experimental soft wrap implementation`() {
    doTestVisualLineStartOffsetWithSoftWrapImplementation(useNewSoftWraps = true)
  }

  fun `test visualLineStartOffset with experimental custom soft wrap`() {
    setUpCustomWrapSupport()
    initText("abcdefghi")

    assertNotNull(editor.customWrapModel.runBatchMutation { addWrap(4) })

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(4, editorImpl.visualLineStartOffset(1))
  }

  fun `test visualLineStartOffset aligns binary search result from inside surrogate pair`() {
    initText("a\n${SURROGATE_PAIR}bcde")

    assertEquals(2, editorImpl.visualLineStartOffset(1))
  }

  private fun doTestVisualLineStartOffsetWithSoftWrapImplementation(useNewSoftWraps: Boolean) {
    Registry.get("editor.use.new.soft.wraps.impl").setValue(useNewSoftWraps, getTestRootDisposable())
    initText("abcdefghi")
    configureSoftWraps(4)
    verifySoftWrapPositions(4, 7)

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(4, editorImpl.visualLineStartOffset(1))
    assertEquals(7, editorImpl.visualLineStartOffset(2))
    assertEquals(editor.document.textLength, editorImpl.visualLineStartOffset(3))
  }

  private fun doTestOffsetToVisualLineWithSoftWrapImplementation(useNewSoftWraps: Boolean) {
    Registry.get("editor.use.new.soft.wraps.impl").setValue(useNewSoftWraps, getTestRootDisposable())
    initText("abcdefghi")
    configureSoftWraps(4)
    verifySoftWrapPositions(4, 7)

    assertEquals(0, editorImpl.offsetToVisualLine(4, true))
    assertEquals(1, editorImpl.offsetToVisualLine(4, false))
    assertEquals(1, editorImpl.offsetToVisualLine(7, true))
    assertEquals(2, editorImpl.offsetToVisualLine(7, false))
  }

  private fun assertVisualLineStartOffsets(vararg expectedOffsets: Int) {
    expectedOffsets.forEachIndexed { visualLine, expectedOffset ->
      assertEquals(expectedOffset, editorImpl.visualLineStartOffset(visualLine))
    }
  }

  private val editorImpl: EditorImpl
    get() = editor as EditorImpl
}
