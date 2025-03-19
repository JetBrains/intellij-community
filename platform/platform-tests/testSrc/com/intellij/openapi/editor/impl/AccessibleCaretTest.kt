// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.fileTypes.PlainTextFileType
import javax.accessibility.AccessibleContext

class AccessibleCaretTest : AbstractEditorTest() {
  fun `test accessible caret property change on backspace`() {
    val accessibleCaret = initEditor(3)

    backspace()
    assertEquals(2, accessibleCaret.position)
    backspace()
    assertEquals(1, accessibleCaret.position)
    backspace()
    assertEquals(0, accessibleCaret.position)
  }

  fun `test accessible caret property change on indent and unindent`() {
    val accessibleCaret = initEditor(0)

    indent()
    val indentOptions = currentCodeStyleSettings.getIndentOptions(PlainTextFileType.INSTANCE)
    assertEquals(indentOptions.INDENT_SIZE, accessibleCaret.position)
    unindent()
    assertEquals(0, accessibleCaret.position)
  }

  private fun initEditor(initialCaretPosition: Int): CaretPosition {
    val caretPosition = CaretPosition(initialCaretPosition)
    initText("12345")
    editor.caretModel.moveToOffset(caretPosition.position)
    editor.contentComponent.accessibleContext.addPropertyChangeListener { evt ->
      if (evt.propertyName == AccessibleContext.ACCESSIBLE_CARET_PROPERTY) {
        caretPosition.position = evt.newValue as Int
      }
    }
    return caretPosition
  }

  private class CaretPosition(var position: Int)
}
