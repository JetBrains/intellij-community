// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase

class CustomFileTypeAutopopupTest : CompletionAutoPopupTestCase() {

  fun testNoAutopopupWhenTypingJustDigitInACustomFileType() {
    myFixture.configureByText("a.hs", "a42 = 42\n<caret> }}")
    type("4")
    assertNull(lookup)
  }

  fun testShowAutopopupWhenTypingDigitAfterLetter() {
    myFixture.configureByText("a.hs", "a42 = 42\na<caret> }}")
    type("4")
    assertNotNull(lookup)
  }
}