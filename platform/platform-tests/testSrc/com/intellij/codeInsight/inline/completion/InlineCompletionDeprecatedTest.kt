// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Key
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class InlineCompletionDeprecatedTest : InlineCompletionTestCase() {

  @Suppress("DEPRECATION")
  @Test
  fun `test old suggestion builder`() = myFixture.testInlineCompletion {
    val key = Key.create<Int>("inline.completion.test.key")
    val provider = object : InlineCompletionProvider {
      override val id: InlineCompletionProviderID = InlineCompletionProviderID("TEST")

      override fun isEnabled(event: InlineCompletionEvent): Boolean {
        return event is InlineCompletionEvent.DirectCall || event is InlineCompletionEvent.DocumentChange
      }

      override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
        return object : com.intellij.codeInsight.inline.completion.InlineCompletionSuggestion() {
          override val suggestionFlow: Flow<InlineCompletionElement> = flow {
            putUserData(key, 42)
            emit(InlineCompletionGrayTextElement("1234"))
            emit(InlineCompletionGrayTextElement("abcd"))
          }
        }
      }
    }
    InlineCompletionHandler.registerTestHandler(provider)

    init(PlainTextFileType.INSTANCE)
    callInlineCompletion()
    delay()
    assertInlineRender("1234abcd")
    assertAllVariants("1234abcd")
    nextVariant()
    nextVariant()
    prevVariant()
    assertInlineRender("1234abcd")
    typeChar('1')
    assertInlineRender("234abcd")
    insert()
    assertInlineHidden()
    assertFileContent("1234abcd<caret>")
  }
}
