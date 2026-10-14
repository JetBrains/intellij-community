// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class RelativeLineHelperTests {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun `test relative line above caret`(): Unit = fixture.runTest {
    initText("line 1\nline 2\nline 3\nline 4\nline 5\n")
    checkRelativeLine(-1, 2, 1)
  }

  @Test
  fun `test relative line below caret`(): Unit = fixture.runTest {
    initText("line 1\nline 2\nline 3\nline 4\nline 5\n")
    checkRelativeLine(1, 2, 3)
  }

  @Test
  fun `test relative line at caret`(): Unit = fixture.runTest {
    initText("line 1\nline 2\nline 3\nline 4\nline 5\n")
    checkRelativeLine(0, 2, 2)
  }

  @Test
  fun `test hybrid line at caret`(): Unit = fixture.runTest {
    initText("line 1\nline 2\nline 3\nline 4\nline 5\n")
    assertEquals(3, RelativeLineHelper.getHybridLine(editor, 2, 2))
  }

  @Test
  fun `test relative line above caret in fold`(): Unit = fixture.runTest {
    initText("line 1\nline 2\nline 3\nline 4\nline 5\n")
    foldLines(2, 3)
    checkRelativeLine(-1, 2, 1)
  }

  @Test
  fun `test relative line below caret in fold`(): Unit = fixture.runTest {
    initText("line 1\nline 2\nline 3\nline 4\nline 5\n")
    foldLines(2, 3)
    checkRelativeLine(1, 2, 4)
  }

  @Test
  fun `test relative line at caret in fold`(): Unit = fixture.runTest {
    initText("line 1\nline 2\nline 3\nline 4\nline 5\n")
    foldLines(2, 3)
    checkRelativeLine(0, 2, 2)
  }

  @Test
  fun `test hybrid line at caret in fold`(): Unit = fixture.runTest {
    initText("line 1\nline 2\nline 3\nline 4\nline 5\n")
    foldLines(2, 3)
    assertEquals(3, RelativeLineHelper.getHybridLine(editor, 2, 2))
  }

  @Test
  fun `test line number for fold below caret`(): Unit = fixture.runTest {
    initText("line 1\nline 2\nline 3\nline 4\nline 5\n")
    foldLines(2, 3)
    checkRelativeLine(1, 1, 2)
    assertEquals(1, RelativeLineHelper.getRelativeLine(editor, 1, 3))
    checkRelativeLine(2, 1, 4)
    checkRelativeLine(0, 1, 1)
  }

  @Test
  fun `test line number for fold above caret`(): Unit = fixture.runTest {
    initText("line 1\nline 2\nline 3\nline 4\nline 5\n")
    foldLines(2, 3)
    checkRelativeLine(-2, 4, 1)
    checkRelativeLine(-1, 4, 2)
    assertEquals(-1, RelativeLineHelper.getRelativeLine(editor, 4, 3))
    checkRelativeLine(0, 4, 4)
  }

  @Test
  fun `test fold inside line`(): Unit = fixture.runTest {
    val text = """
      line 0
      line 1
      
      aaa
      
      
      bbb
      
      line 5
    """.trimIndent()
    initText(text)

    foldRegion(text.indexOf("aaa") + 3, text.indexOf("bbb"))
    checkRelativeLine(-3, 3, 0)
    checkRelativeLine(-2, 3, 1)
    checkRelativeLine(-1, 3, 2)
    checkRelativeLine(0, 3, 3)
    assertEquals(0, RelativeLineHelper.getRelativeLine(editor, 3, 4))
    assertEquals(0, RelativeLineHelper.getRelativeLine(editor, 3, 5))
    assertEquals(0, RelativeLineHelper.getRelativeLine(editor, 3, 6))
    checkRelativeLine(1, 3, 7)
    checkRelativeLine(2, 3, 8)

    checkRelativeLine(-3, 4, 0)
    checkRelativeLine(-2, 4, 1)
    checkRelativeLine(-1, 4, 2)
    checkRelativeLine(0, 4, 3)
    assertEquals(0, RelativeLineHelper.getRelativeLine(editor, 4, 4))
    assertEquals(0, RelativeLineHelper.getRelativeLine(editor, 4, 5))
    assertEquals(0, RelativeLineHelper.getRelativeLine(editor, 4, 6))
    checkRelativeLine(1, 4, 7)
    checkRelativeLine(2, 4, 8)
  }

  /**
   * @param endLine is inclusive
   */
  private fun foldLines(startLine: Int, endLine: Int) {
    val startOffset = fixture.editor.document.getLineStartOffset(startLine)
    val endOffset = fixture.editor.document.getLineEndOffset(endLine)
    foldRegion(startOffset, endOffset)
  }

  private fun foldRegion(startOffset: Int, endOffset: Int) {
    val foldingModel = fixture.editor.foldingModel
    foldingModel.runBatchFoldingOperation {
      val foldRegion = fixture.editor.foldingModel.addFoldRegion(startOffset, endOffset, "...")
      foldRegion?.isExpanded = false
    }
  }

  private fun checkRelativeLine(expectedRelativeLine: Int, caretLine: Int, logicalLine: Int) {
    assertEquals(expectedRelativeLine, RelativeLineHelper.getRelativeLine(fixture.editor, caretLine, logicalLine))
    assertEquals(logicalLine, RelativeLineHelper.getLogicalLine(fixture.editor, caretLine, expectedRelativeLine))
  }
}