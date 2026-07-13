// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.openapi.editor.impl.EditorViewAccessor

class LogicalPositionCacheTest : AbstractEditorTest() {
  fun `test empty document and out of range positions`() {
    initText("")
    assertOffset(line=0, column=10, expectedOffset=0)
    assertOffset(line=10, column=0, expectedOffset=0)
    assertLogicalPosition(offset=-1, expectedLine=0, expectedColumn=0)
    assertLogicalPosition(offset=0, expectedLine=0, expectedColumn=0)
    checkConsistency()
  }

  fun `test simple multiline text conversions`() {
    initText("ab\ncde\n")

    assertLogicalPosition(offset=0, expectedLine=0, expectedColumn=0)
    assertLogicalPosition(offset=2, expectedLine=0, expectedColumn=2)
    assertLogicalPosition(offset=3, expectedLine=1, expectedColumn=0)
    assertLogicalPosition(offset=6, expectedLine=1, expectedColumn=3)
    assertLogicalPosition(offset=7, expectedLine=2, expectedColumn=0)

    assertOffset(line=0, column=0, expectedOffset=0)
    assertOffset(line=0, column=2, expectedOffset=2)
    assertOffset(line=1, column=0, expectedOffset=3)
    assertOffset(line=1, column=3, expectedOffset=6)
    assertOffset(line=2, column=0, expectedOffset=7)
    checkConsistency()
  }

  fun `test tabs and surrogate pairs`() {
    initText("a\t${SURROGATE_PAIR}b")

    val tabSize = currentTabSize()
    assertLogicalPosition(offset=0, expectedLine=0, expectedColumn=0)
    assertLogicalPosition(offset=1, expectedLine=0, expectedColumn=1)
    assertLogicalPosition(offset=2, expectedLine=0, expectedColumn=tabSize)
    assertLogicalPosition(offset=3, expectedLine=0, expectedColumn=tabSize)
    assertLogicalPosition(offset=4, expectedLine=0, expectedColumn=tabSize+1)
    assertLogicalPosition(offset=5, expectedLine=0, expectedColumn=tabSize+2)

    assertOffset(line=0, column=0,         expectedOffset=0)
    assertOffset(line=0, column=1,         expectedOffset=1)
    assertOffset(line=0, column=tabSize-1, expectedOffset=1)
    assertOffset(line=0, column=tabSize,   expectedOffset=2)
    assertOffset(line=0, column=tabSize+1, expectedOffset=4)
    assertOffset(line=0, column=tabSize+2, expectedOffset=5)
    checkConsistency()
  }

  fun `test tabs`() {
    initText("a\tb")

    val tabSize = currentTabSize()
    assertLogicalPosition(offset=0, expectedLine=0, expectedColumn=0)
    assertLogicalPosition(offset=1, expectedLine=0, expectedColumn=1)
    assertLogicalPosition(offset=2, expectedLine=0, expectedColumn=tabSize)
    assertLogicalPosition(offset=3, expectedLine=0, expectedColumn=tabSize+1)

    assertOffset(line=0, column=0,         expectedOffset=0)
    assertOffset(line=0, column=1,         expectedOffset=1)
    assertOffset(line=0, column=tabSize-1, expectedOffset=1)
    assertOffset(line=0, column=tabSize,   expectedOffset=2)
    assertOffset(line=0, column=tabSize+1, expectedOffset=3)
    checkConsistency()
  }

  fun `test surrogate pairs`() {
    initText("a${SURROGATE_PAIR}b")

    assertLogicalPosition(offset=0, expectedLine=0, expectedColumn=0)
    assertLogicalPosition(offset=1, expectedLine=0, expectedColumn=1)
    assertLogicalPosition(offset=2, expectedLine=0, expectedColumn=1)
    assertLogicalPosition(offset=3, expectedLine=0, expectedColumn=2)
    assertLogicalPosition(offset=4, expectedLine=0, expectedColumn=3)

    assertOffset(line=0, column=0, expectedOffset=0)
    assertOffset(line=0, column=1, expectedOffset=1)
    assertOffset(line=0, column=2, expectedOffset=3)
    assertOffset(line=0, column=3, expectedOffset=4)
    checkConsistency()
  }

  fun `test long line uses cached anchors`() {
    initText("x".repeat(1024) + "\t" + SURROGATE_PAIR + "z")

    val tabSize = currentTabSize()
    assertLogicalPosition(offset=1024, expectedLine=0, expectedColumn=1024)
    assertLogicalPosition(offset=1025, expectedLine=0, expectedColumn=1024+tabSize)
    assertLogicalPosition(offset=1026, expectedLine=0, expectedColumn=1024+tabSize)
    assertLogicalPosition(offset=1027, expectedLine=0, expectedColumn=1025+tabSize)
    assertLogicalPosition(offset=1028, expectedLine=0, expectedColumn=1026+tabSize)

    assertOffset(line=0, column=1024,           expectedOffset=1024)
    assertOffset(line=0, column=1024+tabSize-1, expectedOffset=1024)
    assertOffset(line=0, column=1024+tabSize,   expectedOffset=1025)
    assertOffset(line=0, column=1025+tabSize,   expectedOffset=1027)
    assertOffset(line=0, column=1026+tabSize,   expectedOffset=1028)
    checkConsistency()
  }

  fun `test cache is invalidated after replacing trivial line with tab`() {
    initText("abcdef")
    assertOffset(line=0, column=3, expectedOffset=3)
    runWriteCommand {
      editor.document.replaceString(1, 3, "\t")
    }
    val tabSize = currentTabSize()
    assertLogicalPosition(offset=editor.document.textLength, expectedLine=0, expectedColumn=tabSize+3)
    assertOffset(line=0, column=tabSize+1, expectedOffset=3)
    checkConsistency()
  }

  fun `test cache preserves trivial inserted lines`() {
    val firstLine = "before\n"
    initText("${firstLine}after")
    assertOffset(line=0, column=3, expectedOffset=3)
    assertOffset(line=1, column=2, expectedOffset= firstLine.length+2)
    assertLogicalPosition(offset=firstLine.length+2, expectedLine=1, expectedColumn=2)
    runWriteCommand {
      editor.document.insertString("before".length, "\nplain")
    }
    assertOffset(line=0, column=3, expectedOffset=3)
    assertOffset(line=1, column=4, expectedOffset=firstLine.length+4)
    assertOffset(line=2, column=2, expectedOffset="${firstLine}plain\n".length+2)
    assertLogicalPosition(offset="${firstLine}plain\n".length+2, expectedLine=2, expectedColumn=2)
    checkConsistency()
  }

  fun `test cache is invalidated after removing lines`() {
    val twoLines = "first\nsecond\n"
    initText(twoLines + "third")
    assertOffset(line=2, column=2, expectedOffset=twoLines.length+2)
    runWriteCommand {
      editor.document.deleteString("first\n".length, twoLines.length)
    }
    assertLogicalPosition("first\n".length + 2, expectedLine=1, expectedColumn=2)
    assertOffset(line=1, column=2, expectedOffset= "first\n".length + 2)
    checkConsistency()
  }

  fun `test cache is invalidated after tab size change`() {
    initText("a\tb")
    editor.settings.setTabSize(4)
    assertLogicalPosition(offset=2, expectedLine=0, expectedColumn=4)
    editor.settings.setTabSize(8)
    assertOffset(line=0, column=7, expectedOffset=1)
    assertOffset(line=0, column=8, expectedOffset=2)
    assertLogicalPosition(offset=2, expectedLine=0, expectedColumn=8)
    checkConsistency()
  }

  fun `test whole text replacement restores caret using current document`() {
    initText("a\tb\nsame")
    editor.settings.setTabSize(4)
    editor.caretModel.moveToLogicalPosition(LogicalPosition(0, 4))
    assertEquals(2, editor.caretModel.offset)

    runWriteCommand {
      editor.document.setText("abcd\nsame")
    }

    assertEquals(4, editor.caretModel.offset)
    checkConsistency()
  }

  private fun assertOffset(line: Int, column: Int, expectedOffset: Int) {
    val actual = editor.logicalPositionToOffset(LogicalPosition(line, column))
    assertEquals(expectedOffset, actual)
  }

  private fun assertLogicalPosition(offset: Int, expectedLine: Int, expectedColumn: Int) {
    val expected = LogicalPosition(expectedLine, expectedColumn)
    val actual = editor.offsetToLogicalPosition(offset)
    assertEquals(expected, actual)
  }

  private fun currentTabSize(): Int {
    return EditorViewAccessor.getView(editor).tabSize
  }

  private fun checkConsistency() {
    EditorViewAccessor.getView(editor).logicalPositionCache.validateState()
  }
}
