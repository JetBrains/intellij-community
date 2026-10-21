// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.EditorModelFixture.Companion.SURROGATE_PAIR
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.DocumentUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.BorderFactory
import kotlin.test.assertFailsWith

@TestApplication
class EditorMappingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun `test yToVisualLine with top inset`(): Unit = fixture.runTest {
    initText("foo\nbar")
    editor.contentComponent.border = BorderFactory.createEmptyBorder(5, 0, 0, 0)
    val y = editor.visualLineToY(1)
    assertEquals(1, editor.yToVisualLine(y))
  }

  @Test
  fun `test visualLineStartOffset clamps non-positive and past-last visual lines`(): Unit = fixture.runTest {
    initText("abc\ndef")

    assertEquals(0, editorImpl.visualLineStartOffset(-1))
    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(editor.document.textLength, editorImpl.visualLineStartOffset(editorImpl.visibleLineCount))
    assertEquals(editor.document.textLength, editorImpl.visualLineStartOffset(editorImpl.visibleLineCount + 1))
  }

  @Test
  fun `test visualLineStartOffset returns zero for empty document`(): Unit = fixture.runTest {
    initText("")

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(0, editorImpl.visualLineStartOffset(1))
  }

  @Test
  fun `test visualLineStartOffset is not allowed during bulk document update`(): Unit = fixture.runTest {
    initText("abc")

    assertFailsWith<RuntimeException> {
      DocumentUtil.executeInBulk(editor.document as DocumentEx) {
        editorImpl.visualLineStartOffset(0)
      }
    }
  }

  @Test
  fun `test visualLineStartOffset is not allowed during batch inlay update`(): Unit = fixture.runTest {
    initText("abc")

    assertFailsWith<IllegalStateException> {
      editor.inlayModel.execute(true) {
        editorImpl.visualLineStartOffset(0)
      }
    }
  }

  @Test
  fun `test offsetToVisualLine returns zero for negative offsets and empty document`(): Unit = fixture.runTest {
    initText("abc")

    assertEquals(0, editorImpl.offsetToVisualLine(-1, false))
    assertEquals(0, editorImpl.offsetToVisualLine(-1, true))

    initText("")

    assertEquals(0, editorImpl.offsetToVisualLine(0, false))
    assertEquals(0, editorImpl.offsetToVisualLine(1, true))
  }

  @Test
  fun `test offsetToVisualLine is not allowed during bulk document update`(): Unit = fixture.runTest {
    initText("abc")

    assertFailsWith<RuntimeException> {
      DocumentUtil.executeInBulk(editor.document as DocumentEx) {
        editorImpl.offsetToVisualLine(0, false)
      }
    }
  }

  @Test
  fun `test offsetToVisualLine is not allowed during batch inlay update`(): Unit = fixture.runTest {
    initText("abc")

    assertFailsWith<IllegalStateException> {
      editor.inlayModel.execute(true) {
        editorImpl.offsetToVisualLine(0, false)
      }
    }
  }

  @Test
  fun `test offsetToVisualLine clamps offsets past document end`(): Unit = fixture.runTest {
    initText("abc\ndef")

    assertEquals(1, editorImpl.offsetToVisualLine(editor.document.textLength, false))
    assertEquals(1, editorImpl.offsetToVisualLine(editor.document.textLength + 10, false))
    assertEquals(1, editorImpl.offsetToVisualLine(editor.document.textLength + 10, true))
  }

  @Test
  fun `test offsetToVisualLine returns logical lines without wraps or folds`(): Unit = fixture.runTest {
    initText("abc\ndef\nghi")

    assertEquals(0, editorImpl.offsetToVisualLine(0, false))
    assertEquals(0, editorImpl.offsetToVisualLine(3, false))
    assertEquals(1, editorImpl.offsetToVisualLine(4, false))
    assertEquals(1, editorImpl.offsetToVisualLine(7, true))
    assertEquals(2, editorImpl.offsetToVisualLine(8, false))
  }

  @Test
  fun `test offsetToVisualLine returns logical lines when soft wraps are enabled but none are registered`(): Unit = fixture.runTest {
    initText("abc\ndef")
    configureSoftWraps(100)
    verifySoftWrapPositions()

    assertEquals(0, editorImpl.offsetToVisualLine(0, false))
    assertEquals(1, editorImpl.offsetToVisualLine(4, false))
  }

  @Test
  fun `test offsetToVisualLine ignores registered soft wraps when soft wrapping is disabled`(): Unit = fixture.runTest {
    initText("abcdefghi\nxyz")
    configureSoftWraps(4)
    verifySoftWrapPositions(4, 7)

    editor.settings.setUseSoftWraps(false)

    assertEquals(0, editorImpl.offsetToVisualLine(4, false))
    assertEquals(0, editorImpl.offsetToVisualLine(8, false))
    assertEquals(1, editorImpl.offsetToVisualLine(10, false))
  }

  @Test
  fun `test offsetToVisualLine distinguishes offsets before and after exact soft wrap`(): Unit = fixture.runTest {
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

  @Test
  fun `test offsetToVisualLine with custom soft wrap`(): Unit = fixture.runTest {
    setUpCustomWrapSupport()
    initText("abcdefghi")

    assertNotNull(editor.customWrapModel.runBatchMutation { addWrap(4) })

    assertEquals(0, editorImpl.offsetToVisualLine(4, true))
    assertEquals(1, editorImpl.offsetToVisualLine(4, false))
    assertEquals(1, editorImpl.offsetToVisualLine(5, false))
  }

  @Test
  fun `test offsetToVisualLine aligns offset inside surrogate pair`(): Unit = fixture.runTest {
    initText("a\n${SURROGATE_PAIR}bc")

    assertEquals(editorImpl.offsetToVisualLine(2, false), editorImpl.offsetToVisualLine(3, false))
  }

  @Test
  fun `test offsetToVisualLine maps collapsed fold start and interior to the same visual line`(): Unit = fixture.runTest {
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

  @Test
  fun `test offsetToVisualLine returns zero inside whole-document collapsed fold`(): Unit = fixture.runTest {
    initText("aaa\nbbbb\ncccc")
    addCollapsedFoldRegion(0, editor.document.textLength, "...")

    assertEquals(0, editorImpl.offsetToVisualLine(0, false))
    assertEquals(0, editorImpl.offsetToVisualLine(editor.document.getLineStartOffset(1), false))
    assertEquals(0, editorImpl.offsetToVisualLine(editor.document.textLength, false))
  }

  @Test
  fun `test offsetToVisualLine ignores expanded fold region`(): Unit = fixture.runTest {
    initText("aaa\nbbbb\ncccc")
    val foldStartOffset = editor.document.getLineStartOffset(1)
    val foldEndOffset = editor.document.getLineEndOffset(2)
    val foldRegion = addFoldRegion(foldStartOffset, foldEndOffset, "...")!!

    assertNotNull(foldRegion)
    assertTrue(foldRegion.isExpanded)
    assertEquals(1, editorImpl.offsetToVisualLine(foldStartOffset, false))
    assertEquals(2, editorImpl.offsetToVisualLine(editor.document.getLineStartOffset(2), false))
  }

  @Test
  fun `test offsetToVisualLine uses outer collapsed fold for nested folds`(): Unit = fixture.runTest {
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

  @Test
  fun `test visualLineStartOffset returns document line starts without wraps or folds`(): Unit = fixture.runTest {
    initText("abc\ndefghi")

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(4, editorImpl.visualLineStartOffset(1))
  }

  @Test
  fun `test visualLineStartOffset returns start of trailing empty line`(): Unit = fixture.runTest {
    initText("abc\n")

    assertEquals(editor.document.textLength, editorImpl.visualLineStartOffset(1))
  }

  @Test
  fun `test visualLineStartOffset returns document line starts when soft wraps are enabled but none are registered`(): Unit = fixture.runTest {
    initText("abc\ndef")
    configureSoftWraps(100)
    verifySoftWrapPositions()

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(4, editorImpl.visualLineStartOffset(1))
  }

  @Test
  fun `test visualLineStartOffset ignores soft wraps when soft wrapping is disabled`(): Unit = fixture.runTest {
    initText("abcdefghi\nxyz")
    configureSoftWraps(4)
    verifySoftWrapPositions(4, 7)

    editor.settings.setUseSoftWraps(false)

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(10, editorImpl.visualLineStartOffset(1))
    assertEquals(editor.document.textLength, editorImpl.visualLineStartOffset(2))
  }

  @Test
  fun `test visualLineStartOffset returns folded region start when binary search lands inside collapsed fold`(): Unit = fixture.runTest {
    initText("aaa\nbbbb\ncccc\ndddd\neeee")
    val foldStartOffset = editor.document.getLineStartOffset(1)
    val foldEndOffset = editor.document.getLineStartOffset(4)
    addCollapsedFoldRegion(foldStartOffset, foldEndOffset, "...")

    assertEquals(foldStartOffset, editorImpl.visualLineStartOffset(1))
  }

  @Test
  fun `test visualLineStartOffset maps collapsed fold that does not include following line break`(): Unit = fixture.runTest {
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

  @Test
  fun `test visualLineStartOffset maps collapsed fold that ends at following line start`(): Unit = fixture.runTest {
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

  @Test
  fun `test visualLineStartOffset handles adjacent collapsed folds with the same visual start`(): Unit = fixture.runTest {
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

  @Test
  fun `test visualLineStartOffset maps mid-line collapsed fold to containing line start`(): Unit = fixture.runTest {
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

  @Test
  fun `test visualLineStartOffset maps custom fold region`(): Unit = fixture.runTest {
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

  @Test
  fun `test visualLineStartOffset ignores collapsed folds when folding is disabled`(): Unit = fixture.runTest {
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

  @Test
  fun `test visualLineStartOffset ignores single-line collapsed folds`(): Unit = fixture.runTest {
    initText("line 0\nline 1\nline 2")
    addCollapsedFoldRegion(editor.document.getLineStartOffset(1), editor.document.getLineEndOffset(1), "...")

    assertVisualLineStartOffsets(
      editor.document.getLineStartOffset(0),
      editor.document.getLineStartOffset(1),
      editor.document.getLineStartOffset(2),
      editor.document.textLength,
    )
  }

  @Test
  fun `test visualLineStartOffset returns whole-document collapsed fold boundaries`(): Unit = fixture.runTest {
    initText("aaa\nbbbb\ncccc")
    addCollapsedFoldRegion(0, editor.document.textLength, "...")

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(editor.document.textLength, editorImpl.visualLineStartOffset(1))
  }

  @Test
  fun `test visualLineStartOffset ignores expanded fold region`(): Unit = fixture.runTest {
    initText("aaa\nbbbb\ncccc")
    val foldStartOffset = editor.document.getLineStartOffset(1)
    val foldEndOffset = editor.document.getLineEndOffset(2)
    val foldRegion = addFoldRegion(foldStartOffset, foldEndOffset, "...")!!

    assertNotNull(foldRegion)
    assertTrue(foldRegion.isExpanded)
    assertEquals(foldStartOffset, editorImpl.visualLineStartOffset(1))
    assertEquals(editor.document.getLineStartOffset(2), editorImpl.visualLineStartOffset(2))
  }

  @Test
  fun `test visualLineStartOffset uses outer collapsed fold start for nested folds`(): Unit = fixture.runTest {
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

  @Test
  fun `test visualLineStartOffset returns exact soft wrap offset`(): Unit = fixture.runTest {
    initText("abcdefghi")
    configureSoftWraps(4)
    verifySoftWrapPositions(4, 7)

    assertEquals(4, editorImpl.visualLineStartOffset(1))
    assertEquals(7, editorImpl.visualLineStartOffset(2))
  }

  @Test
  fun `test visualLineStartOffset returns previous soft wrap offset`(): Unit = fixture.runTest {
    initText("abcdefgh")
    configureSoftWraps(4)
    verifySoftWrapPositions(4)

    assertEquals(4, editorImpl.visualLineStartOffset(1))
  }

  @Test
  fun `test visualLineStartOffset keeps line start when soft wrap starts at same offset`(): Unit = fixture.runTest {
    initText("abc\ndef")
    addCollapsedFoldRegion(3, 4, "...")
    configureSoftWraps(6)
    verifySoftWrapPositions(4)

    assertEquals(4, editorImpl.visualLineStartOffset(1))
  }

  @Test
  fun `test visualLineStartOffset with custom soft wrap`(): Unit = fixture.runTest {
    setUpCustomWrapSupport()
    initText("abcdefghi")

    assertNotNull(editor.customWrapModel.runBatchMutation { addWrap(4) })

    assertEquals(0, editorImpl.visualLineStartOffset(0))
    assertEquals(4, editorImpl.visualLineStartOffset(1))
  }

  @Test
  fun `test visualLineStartOffset aligns binary search result from inside surrogate pair`(): Unit = fixture.runTest {
    initText("a\n${SURROGATE_PAIR}bcde")

    assertEquals(2, editorImpl.visualLineStartOffset(1))
  }

  private fun assertVisualLineStartOffsets(vararg expectedOffsets: Int) {
    expectedOffsets.forEachIndexed { visualLine, expectedOffset ->
      assertEquals(expectedOffset, editorImpl.visualLineStartOffset(visualLine))
    }
  }

  private val editorImpl: EditorImpl
    get() = fixture.editor as EditorImpl
}
