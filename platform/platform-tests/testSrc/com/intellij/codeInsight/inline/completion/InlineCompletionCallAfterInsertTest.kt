// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileTypes.PlainTextFileType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class InlineCompletionCallAfterInsertTest : InlineCompletionTestCase() {

  @Test
  fun `test 'after insert' event chooses appropriate provider`() = myFixture.testInlineCompletion {
    InlineCompletionProvider.EP_NAME.point.registerExtension(ExceptDirectCallProvider(), LoadingOrder.FIRST, testRootDisposable)
    InlineCompletionProvider.EP_NAME.point.registerExtension(DirectCallProvider(), LoadingOrder.LAST, testRootDisposable)

    init(PlainTextFileType.INSTANCE, "<caret>")
    callInlineCompletion()
    delay()
    assertInlineRender("DIRECT_CALL") // it's the last provider, but the first one is not enabled for direct call
    insert()
    delay()
    assertInlineRender("DIRECT_CALL") // the first provider is enabled, but the second one was inserted
  }

  private class DirectCallProvider : InlineCompletionProvider {
    override val id: InlineCompletionProviderID = InlineCompletionProviderID("DIRECT_CALL")

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
      return event is InlineCompletionEvent.DirectCall || event is InlineCompletionEvent.SuggestionInserted
    }

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
      return InlineCompletionSingleSuggestion.build {
        emit(InlineCompletionGrayTextElement("DIRECT_CALL"))
      }
    }
  }

  private class ExceptDirectCallProvider : InlineCompletionProvider {
    override val id: InlineCompletionProviderID = InlineCompletionProviderID("NOT_DIRECT_CALL")

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
      return event !is InlineCompletionEvent.DirectCall
    }

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
      return InlineCompletionSingleSuggestion.build {
        emit(InlineCompletionGrayTextElement("NOT_DIRECT_CALL"))
      }
    }
  }
}
