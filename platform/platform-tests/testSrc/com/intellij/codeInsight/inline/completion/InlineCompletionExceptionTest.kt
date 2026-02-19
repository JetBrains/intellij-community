// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.impl.SimpleInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.assertLogThrows
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class InlineCompletionExceptionTest : InlineCompletionTestCase() {

  private fun registerSuggestion(vararg suggestion: InlineCompletionElement) {
    InlineCompletionHandler.registerTestHandler(SimpleInlineCompletionProvider(suggestion.toList()), testRootDisposable)
  }

  @Test
  fun `test manually dispose session and use completion after`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion(InlineCompletionGrayTextElement("simple completion"))
    typeChar('\n')
    delay()
    withContext(Dispatchers.EDT) {
      val session = assertNotNull(InlineCompletionSession.getOrNull(myFixture.editor))
      Disposer.dispose(session)
    }
    withContext(Dispatchers.EDT) {
      assertLogThrows<Throwable> {
        escape()
      }
    }
    assertInlineHidden()
    typeChar('\n')
    delay()
    assertInlineRender("simple completion")
  }
}
