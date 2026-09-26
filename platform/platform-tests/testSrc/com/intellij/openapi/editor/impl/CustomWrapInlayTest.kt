// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.testFramework.EditorTestUtil
import java.awt.Point

class CustomWrapInlayTest : AbstractEditorTest() {

  override fun setUp() {
    super.setUp()
    setUpCustomWrapSupport()
  }

  fun testInlaysAtCustomWrapOffsetOccupyDifferentVisualLines() {
    initText("ab")
    assertNotNull(editor.customWrapModel.runBatchMutation { addWrap(1) })

    val afterWrapInlay = addInlay(1, false)
    val beforeWrapInlay = addInlay(1, true)

    assertVisualPosition(afterWrapInlay, 1, 0)
    assertVisualPosition(beforeWrapInlay, 0, 1)
  }

  fun testTypingAtAfterWrapLineMovesOnlyAfterWrapInlay() {
    initText("ab")
    assertNotNull(editor.customWrapModel.runBatchMutation { addWrap(1) })

    val afterWrapInlay = addInlay(1, false)
    val beforeWrapInlay = addInlay(1, true)
    editor.caretModel.moveToVisualPosition(editor.offsetToVisualPosition(1, false, false))

    type('X')

    assertEquals("aXb", editor.document.text)
    assertEquals(2, editor.caretModel.offset)
    assertEquals(1, beforeWrapInlay.offset)
    assertEquals(2, afterWrapInlay.offset)
    assertVisualPosition(beforeWrapInlay, 0, 1)
    assertVisualPosition(afterWrapInlay, 1, 1)
  }

  fun testInlayHitTestingAtCustomWrapOffsetTreatsInlaysAsNonText() {
    initText("ab")
    assertNotNull(editor.customWrapModel.runBatchMutation { addWrap(1) })

    val afterWrapInlay = addInlay(1, false)
    val beforeWrapInlay = addInlay(1, true)

    assertFalse(EditorUtil.isPointOverText(editor, pointInside(beforeWrapInlay)))
    assertFalse(EditorUtil.isPointOverText(editor, pointInside(afterWrapInlay)))
  }

  fun testVisualLineWidthIncludesInlayBeforeCustomWrap() {
    initText("ab")
    assertNotNull(editor.customWrapModel.runBatchMutation { addWrap(1) })

    val view = EditorViewAccessor.getView(editor)
    val afterWrapInlay = EditorTestUtil.addInlay(editor, 1, false, 11)
    val lineWidthWithoutBeforeWrapInlay = view.getPreferredWidth(0, 1)
    val beforeWrapInlay = EditorTestUtil.addInlay(editor, 1, true, 7)
    val lineWidth = view.getPreferredWidth(0, 1)

    assertVisualPosition(afterWrapInlay, 1, 0)
    assertVisualPosition(beforeWrapInlay, 0, 1)
    assertEquals(lineWidthWithoutBeforeWrapInlay + beforeWrapInlay.widthInPixels, lineWidth)
  }

  private fun assertVisualPosition(inlay: Inlay<*>, line: Int, column: Int) {
    assertEquals(line, inlay.visualPosition.line)
    assertEquals(column, inlay.visualPosition.column)
  }

  private fun pointInside(inlay: Inlay<*>): Point {
    val bounds = inlay.bounds
    assertNotNull(bounds)
    return Point(bounds!!.x + bounds.width / 2, bounds.y + bounds.height / 2)
  }
}
