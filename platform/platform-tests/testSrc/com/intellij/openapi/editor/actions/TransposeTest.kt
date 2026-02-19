// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.impl.AbstractEditorTest

class TransposeTest : AbstractEditorTest() {
  fun testMiddleOfLine() {
    initText("ab<caret>cd")
    executeAction(IdeActions.ACTION_EDITOR_TRANSPOSE)
    checkResultByText("acb<caret>d")
  }

  fun testOneCharacterBeforeEOL() {
    initText("abc<caret>d\ndefg")
    executeAction(IdeActions.ACTION_EDITOR_TRANSPOSE)
    checkResultByText("abdc<caret>\ndefg")
  }

  fun testEndOfLine() {
    initText("abcd<caret>\nqwer")
    executeAction(IdeActions.ACTION_EDITOR_TRANSPOSE)
    checkResultByText("abdc<caret>\nqwer")
  }

  fun testStartOfLine() {
    initText("abcd\n<caret>qwer")
    executeAction(IdeActions.ACTION_EDITOR_TRANSPOSE)
    checkResultByText("abcdq\n<caret>wer")
  }

  fun testEndOfOneCharacterLine() {
    initText("abc\nd<caret>\nqwer")
    executeAction(IdeActions.ACTION_EDITOR_TRANSPOSE)
    checkResultByText("abcd\n<caret>\nqwer")
  }

  fun testOneCharacterLine() {
    initText("a<caret>")
    executeAction(IdeActions.ACTION_EDITOR_TRANSPOSE)
    checkResultByText("a<caret>")
  }
}
