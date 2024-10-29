// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionTestCase
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.impl.GradualMultiSuggestInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.logs.TestInlineCompletionLogs.noSessionLogs
import com.intellij.codeInsight.inline.completion.logs.TestInlineCompletionLogs.singleSessionLog
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionBuilder
import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.openapi.fileTypes.PlainTextFileType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class InlineCompletionSessionLogsTest : InlineCompletionTestCase() {
  private lateinit var provider: GradualMultiSuggestInlineCompletionProvider

  private fun registerSuggestion(suggestionBuilder: suspend InlineCompletionSuggestionBuilder.() -> Unit) {
    provider = GradualMultiSuggestInlineCompletionProvider(suggestionBuilder)
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)
  }

  @Test
  fun testNoActionsNoLogs() {
    myFixture.testInlineCompletion {
      noSessionLogs {
        init(PlainTextFileType.INSTANCE)
      }
    }
  }

  @Test
  fun testAcceptedCompletion() {
    myFixture.testInlineCompletion {
      init(PlainTextFileType.INSTANCE)
      val completion = "some completion\nnext line"
      registerSuggestion {
        variant {
          emit(InlineCompletionGrayTextElement(completion))
        }
      }
      val singleSessionLog = singleSessionLog {
        callInlineCompletion()
        provider.computeNextElement()
        delay()
        insert()
      }
      singleSessionLog.assertRequestIdPresent()
      singleSessionLog.assertSomeContextLogsPresent()
      singleSessionLog.assertWasShown(true)
      singleSessionLog.assertFinishType(FinishType.SELECTED)
      singleSessionLog.assertTotalInsertionLogs(completion.length, 2)
    }
  }

  @Test
  fun testCancelledDuringComputationCompletion() {
    myFixture.testInlineCompletion {
      init(PlainTextFileType.INSTANCE)
      registerSuggestion {
        variant {
          emit(InlineCompletionGrayTextElement("some slow completion"))
        }
      }
      val singleSessionLog = singleSessionLog {
        callInlineCompletion()
        // now cancel it
        typeChar('a')
        // it doesn't create the second session log because it hangs infinitely
      }
      singleSessionLog.assertRequestIdPresent()
      singleSessionLog.assertSomeContextLogsPresent()
      singleSessionLog.assertWasShown(false)
      singleSessionLog.assertFinishType(FinishType.INVALIDATED)
      singleSessionLog.assertInvalidationEvent(InlineCompletionEvent.DocumentChange::class.java)
    }
  }

  @Test
  fun testCancelledByTypingWhenShown() {
    myFixture.testInlineCompletion {
      init(PlainTextFileType.INSTANCE)
      registerSuggestion {
        variant {
          emit(InlineCompletionGrayTextElement("hello world!"))
        }
      }
      val singleSessionLog = singleSessionLog {
        callInlineCompletion()
        provider.computeNextElement()
        delay()
        typeChar('a')
        // it doesn't create the second session log because it hangs infinitely
      }
      singleSessionLog.assertRequestIdPresent()
      singleSessionLog.assertSomeContextLogsPresent()
      singleSessionLog.assertWasShown(true)
      singleSessionLog.assertFinishType(FinishType.INVALIDATED)
      singleSessionLog.assertInvalidationEvent(InlineCompletionEvent.DocumentChange::class.java)
    }
  }
}