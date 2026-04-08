// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.util.registry.Registry

class CustomWrapBackspaceTest : AbstractEditorTest() {

  override fun setUp() {
    super.setUp()
    Registry.get("editor.use.new.soft.wraps.impl").setValue(true, getTestRootDisposable())
    Registry.get("editor.custom.soft.wraps.support.enabled").setValue(true, getTestRootDisposable())
  }

  fun testBackspaceRemovesWrapWhenCaretIsAfterCustomWrap() {
    initText("abcd")
    editor.customWrapModel.addWrap(2, 0, 0)!!
    editor.caretModel.moveToVisualPosition(VisualPosition(1, 0))

    backspace()

    checkResultByText("ab<caret>cd")
    assertTrue(editor.customWrapModel.getWraps().isEmpty())
  }

  fun testBackspaceBeforeCustomWrapDeletesCharacter() {
    initText("abcd")
    editor.customWrapModel.addWrap(2, 0, 0)!!
    editor.caretModel.moveToVisualPosition(VisualPosition(0, 2))

    backspace()

    checkResultByText("a<caret>cd")
    assertEquals(1, editor.customWrapModel.getWraps().single().offset)
  }

  fun testBackspaceWithSelectionKeepsCustomWrap() {
    initText("abcd")
    editor.customWrapModel.addWrap(2, 0, 0)!!
    editor.caretModel.moveToVisualPosition(VisualPosition(1, 0))
    val selectedCaret = editor.caretModel.addCaret(VisualPosition(1, 2))
    assertNotNull(selectedCaret)
    selectedCaret!!.setSelection(3, 4)

    backspace()

    checkResultByText("ab<caret>c<caret>")
    assertEquals(listOf(2), editor.customWrapModel.getWraps().map { it.offset })
    assertEquals(1, editor.offsetToVisualLine(2, false))
  }
}
