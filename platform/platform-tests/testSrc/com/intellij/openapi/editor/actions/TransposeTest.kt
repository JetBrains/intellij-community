// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.impl.editorModelFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class TransposeTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun testMiddleOfLine(): Unit = fixture.runTest {
    initText("ab<caret>cd")
    executeAction(IdeActions.ACTION_EDITOR_TRANSPOSE)
    checkResultByText("acb<caret>d")
  }

  @Test
  fun testOneCharacterBeforeEOL(): Unit = fixture.runTest {
    initText("abc<caret>d\ndefg")
    executeAction(IdeActions.ACTION_EDITOR_TRANSPOSE)
    checkResultByText("abdc<caret>\ndefg")
  }

  @Test
  fun testEndOfLine(): Unit = fixture.runTest {
    initText("abcd<caret>\nqwer")
    executeAction(IdeActions.ACTION_EDITOR_TRANSPOSE)
    checkResultByText("abdc<caret>\nqwer")
  }

  @Test
  fun testStartOfLine(): Unit = fixture.runTest {
    initText("abcd\n<caret>qwer")
    executeAction(IdeActions.ACTION_EDITOR_TRANSPOSE)
    checkResultByText("abcdq\n<caret>wer")
  }

  @Test
  fun testEndOfOneCharacterLine(): Unit = fixture.runTest {
    initText("abc\nd<caret>\nqwer")
    executeAction(IdeActions.ACTION_EDITOR_TRANSPOSE)
    checkResultByText("abcd\n<caret>\nqwer")
  }

  @Test
  fun testOneCharacterLine(): Unit = fixture.runTest {
    initText("a<caret>")
    executeAction(IdeActions.ACTION_EDITOR_TRANSPOSE)
    checkResultByText("a<caret>")
  }
}
