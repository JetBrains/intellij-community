// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.impl.ExceptionInComputationInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.impl.ExceptionInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.impl.GradualMultiSuggestInlineCompletionProvider
import com.intellij.openapi.fileTypes.PlainTextFileType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class ExceptionInlineCompletionTest : InlineCompletionTestCase() {

  @Test
  fun `test exception inside provider removes session`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    InlineCompletionHandler.registerTestHandler(ExceptionInlineCompletionProvider(), testRootDisposable)
    callInlineCompletion()
    delay()
    assertInlineHidden()
  }

  @Test
  fun `test exception while computing variants removes session`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    InlineCompletionHandler.registerTestHandler(ExceptionInComputationInlineCompletionProvider(), testRootDisposable)
    callInlineCompletion()
    delay()
    assertInlineHidden()
  }

  @Test
  fun `test exception which is never reached does not remove session`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    val provider = GradualMultiSuggestInlineCompletionProvider {
      variant {
        emit(InlineCompletionGrayTextElement("one"))
        emit(InlineCompletionGrayTextElement("two"))
        emit(InlineCompletionGrayTextElement("three"))
        throw IllegalArgumentException("not expected error")
      }
    }
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)
    callInlineCompletion()
    provider.computeNextElements(2)
    insert()
    assertInlineHidden()
    assertFileContent("onetwo<caret>")
  }
}
