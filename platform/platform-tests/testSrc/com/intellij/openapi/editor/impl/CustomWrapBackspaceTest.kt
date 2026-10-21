// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.VisualPosition
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class CustomWrapBackspaceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @BeforeEach
  fun setUp() {
    fixture.setUpCustomWrapSupport()
  }

  @Test
  fun testBackspaceRemovesWrapWhenCaretIsAfterCustomWrap(): Unit = fixture.runTest {
    initText("abcd")
    assertNotNull(editor.customWrapModel.runBatchMutation { addWrap(2) })
    editor.caretModel.moveToVisualPosition(VisualPosition(1, 0))

    backspace()

    checkResultByText("ab<caret>cd")
    assertTrue(editor.customWrapModel.getWraps().isEmpty())
  }

  @Test
  fun testBackspaceBeforeCustomWrapDeletesCharacter(): Unit = fixture.runTest {
    initText("abcd")
    assertNotNull(editor.customWrapModel.runBatchMutation { addWrap(2) })
    editor.caretModel.moveToVisualPosition(VisualPosition(0, 2))

    backspace()

    checkResultByText("a<caret>cd")
    assertEquals(1, editor.customWrapModel.getWraps().single().offset)
  }

  @Test
  fun testBackspaceWithSelectionKeepsCustomWrap(): Unit = fixture.runTest {
    initText("abcd")
    assertNotNull(editor.customWrapModel.runBatchMutation { addWrap(2) })
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
