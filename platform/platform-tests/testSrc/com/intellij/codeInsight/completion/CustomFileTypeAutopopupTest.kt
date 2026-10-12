// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class CustomFileTypeAutopopupTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val lookup: LookupImpl? get() = CompletionAutoPopupTestUtil.getLookup(myFixture)

  private fun type(s: String) = CompletionAutoPopupTestUtil.type(myFixture, s)

  private fun runTest(block: () -> Unit) = CompletionAutoPopupTestUtil.runWithAutoPopupEnabled<Throwable>(myFixture, block)

  @Test
  fun testNoAutopopupWhenTypingJustDigitInACustomFileType(): Unit = runTest {
    myFixture.configureByText("a.hs", "a42 = 42\n<caret> }}")
    type("4")
    assertNull(lookup)
  }

  @Test
  fun testShowAutopopupWhenTypingDigitAfterLetter(): Unit = runTest {
    myFixture.configureByText("a.hs", "a42 = 42\na<caret> }}")
    type("4")
    assertNotNull(lookup)
  }
}