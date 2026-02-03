// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.event

import com.intellij.codeInsight.inline.completion.*
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager.UpdateResult
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.fileTypes.PlainTextFileType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class InlineCompletionBackspaceTest : InlineCompletionTestCase() {

  @Test
  fun `test backspace with inline completion`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE, "first  <caret>")
    val provider = MyProvider("second", triggerOnBackspace = true)
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)
    backSpace()
    delay()
    assertInlineRender("second")
    typeChar('s')
    assertInlineRender("econd")
    backSpace()
    delay() // a new session is started
    assertInlineRender("second")
  }

  private class MyProvider(
    private val suggestion: String,
    private val triggerOnBackspace: Boolean,
  ) : InlineCompletionProvider {

    override val id: InlineCompletionProviderID = InlineCompletionProviderID("MyProvider")

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
      return event is InlineCompletionEvent.DirectCall || (triggerOnBackspace && event is InlineCompletionEvent.Backspace)
    }

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
      return InlineCompletionSingleSuggestion.build {
        emit(InlineCompletionGrayTextElement(suggestion))
      }
    }

    override val suggestionUpdateManager = object : InlineCompletionSuggestionUpdateManager.Default() {
      override fun onBackspace(event: InlineCompletionEvent.Backspace, variant: InlineCompletionVariant.Snapshot): UpdateResult {
        error("Impossible to update a session when backspace is pressed")
      }
    }
  }
}
