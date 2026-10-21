// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.accessibility.AccessibleContext

@TestApplication
class AccessibleCaretTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun `test accessible caret property change on left and right arrow press`(): Unit = fixture.runTest {
    val accessibleCaret = initEditor(3)

    left()
    assertEquals(2, accessibleCaret.position)
    right()
    assertEquals(3, accessibleCaret.position)
  }

  @Test
  fun `test accessible caret property change on backspace`(): Unit = fixture.runTest {
    val accessibleCaret = initEditor(3)

    backspace()
    assertEquals(2, accessibleCaret.position)
    backspace()
    assertEquals(1, accessibleCaret.position)
    backspace()
    assertEquals(0, accessibleCaret.position)
  }

  @Test
  fun `test accessible caret property change on indent and unindent`(): Unit = fixture.runTest {
    val accessibleCaret = initEditor(0)

    indent()
    val indentOptions = CodeStyle.getSettings(projectFixture.get()).getIndentOptions(PlainTextFileType.INSTANCE)
    assertEquals(indentOptions.INDENT_SIZE, accessibleCaret.position)
    unindent()
    assertEquals(0, accessibleCaret.position)
  }

  private fun initEditor(initialCaretPosition: Int): CaretPosition {
    val caretPosition = CaretPosition(initialCaretPosition)
    fixture.initText("12345")
    fixture.editor.caretModel.moveToOffset(caretPosition.position)
    fixture.editor.contentComponent.accessibleContext.addPropertyChangeListener { evt ->
      if (evt.propertyName == AccessibleContext.ACCESSIBLE_CARET_PROPERTY || evt.propertyName == AccessibleContext.ACCESSIBLE_TEXT_PROPERTY) {
        caretPosition.position = evt.newValue as Int
      }
    }
    return caretPosition
  }

  private class CaretPosition(var position: Int)
}
