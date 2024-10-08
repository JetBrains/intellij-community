// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.assertions.Assertions
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.atomic.AtomicInteger

@RunWith(JUnit4::class)
internal class InlineCompletionNestedEventInvocationTest : InlineCompletionTestCase() {

  @Test
  fun `test do not process event that is sent when updating`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)

    class ForbiddenEvent : InlineCompletionEvent {
      override fun toRequest(): InlineCompletionRequest {
        return Assertions.fail("This event must not be processed")
      }
    }

    class CustomSessionUpdater(private val onSuccess: () -> Unit) : InlineCompletionSuggestionUpdateManager {
      override fun update(
        event: InlineCompletionEvent,
        variant: InlineCompletionVariant.Snapshot
      ): InlineCompletionSuggestionUpdateManager.UpdateResult {
        assertFalse("This event must not appear.", event is ForbiddenEvent)
        InlineCompletion.getHandlerOrNull(myFixture.editor)!!.invokeEvent(ForbiddenEvent())
        onSuccess()
        return InlineCompletionSuggestionUpdateManager.UpdateResult.Same
      }
    }

    class CustomProvider(onSuccessUpdate: () -> Unit) : InlineCompletionProvider {
      override val id: InlineCompletionProviderID = InlineCompletionProviderID("CustomProvider")

      override val suggestionUpdateManager = CustomSessionUpdater(onSuccessUpdate)

      override fun isEnabled(event: InlineCompletionEvent): Boolean {
        return event is InlineCompletionEvent.DirectCall || event is InlineCompletionEvent.DocumentChange
      }

      override suspend fun getSuggestion(request: InlineCompletionRequest) = InlineCompletionSingleSuggestion.build {
        emit(InlineCompletionGrayTextElement("Test"))
      }
    }

    val updateCounter = AtomicInteger(0)
    val onSuccessUpdate: () -> Unit = { updateCounter.incrementAndGet() }
    InlineCompletionHandler.registerTestHandler(CustomProvider(onSuccessUpdate), testRootDisposable)
    callInlineCompletion()
    delay()
    typeChar(':')
    assertInlineRender("Test")
    insert()
    assertInlineHidden()
    assertFileContent(":Test<caret>")

    assertEquals(updateCounter.get(), 1)
  }
}
