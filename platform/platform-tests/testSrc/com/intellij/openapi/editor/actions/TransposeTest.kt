// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TransposeTest : BasePlatformTestCase() {
  fun testMiddleOfLine() {
    myFixture.configureByText("foo.txt", "ab<caret>cd")
    myFixture.performEditorAction("EditorTranspose")
    myFixture.checkResult("acb<caret>d")
  }

  fun testEndOfLine() {
    myFixture.configureByText("foo.txt", "abcd<caret>\nqwer")
    myFixture.performEditorAction("EditorTranspose")
    myFixture.checkResult("abdc<caret>\nqwer")
  }

  fun testStartOfLine() {
    myFixture.configureByText("foo.txt", "abcd\n<caret>qwer")
    myFixture.performEditorAction("EditorTranspose")
    myFixture.checkResult("abcdq\n<caret>wer")
  }

  fun testEndOfOneCharacterLine() {
    myFixture.configureByText("foo.txt", "abc\nd<caret>\nqwer")
    myFixture.performEditorAction("EditorTranspose")
    myFixture.checkResult("abcd\n<caret>\nqwer")
  }
}
