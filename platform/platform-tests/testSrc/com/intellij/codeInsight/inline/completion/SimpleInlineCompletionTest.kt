// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class SimpleInlineCompletionTest : BasePlatformTestCase() {

  private fun registerSuggestion(vararg suggestion: InlineCompletionElement) {
    InlineCompletionHandler.registerTestHandler(SimpleInlineCompletionProvider(suggestion.toList()), testRootDisposable)
  }

  // !!! VERY IMPORTANT !!!
  override fun runInDispatchThread(): Boolean {
    return false
  }

  @Test
  fun `test inline completion renders on typings`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE, "fu<caret>")
    registerSuggestion(InlineCompletionGrayTextElement(" main(args: List<String>) { }"))

    typeChar('n') // Type a symbol into the editor
    delay() // Waiting for request to inline completion to finish
    assertInlineRender(" main(args: List<String>) { }") // Checking what is shown with inlays
    insert()
    assertFileContent("fun main(args: List<String>) { }<caret>")
    assertInlineHidden()
  }

  @Test
  fun `test inline completion does not render on direct action call`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE, "")
    registerSuggestion(InlineCompletionGrayTextElement("This is tutorial"))
    callInlineCompletion() // Direct action call
    delay()
    assertInlineHidden()
  }

  @Test
  fun `test inline completion renders skip elements`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE, "<caret>)")
    registerSuggestion(
      InlineCompletionGrayTextElement("1"),
      InlineCompletionSkipTextElement(")"),
      InlineCompletionGrayTextElement(" + (2)")
    )
    typeChar('(')
    assertFileContent("(<caret>)")
    delay()

    assertInlineElements {
      gray("1")
      skip(")")
      gray(" + (2)")
    }
    insert()
    assertFileContent("(1) + (2)<caret>")
    assertInlineHidden()
  }

  @Test
  fun `test inline completion simple over typing`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE, "<caret>")
    registerSuggestion(InlineCompletionGrayTextElement("his is tutorial"))
    typeChar('T')
    delay()
    assertInlineRender("his is tutorial")
    typeChar('h') // No 'delay' as this update is called instantly
    assertInlineRender("is is tutorial")
    typeChar('i')
    assertInlineRender("s is tutorial")

    assertFileContent("Thi<caret>")
    insert()
    assertFileContent("This is tutorial<caret>")
    assertInlineHidden()
  }

  private class SimpleInlineCompletionProvider(val suggestion: List<InlineCompletionElement>) : InlineCompletionProvider {
    override val id: InlineCompletionProviderID = InlineCompletionProviderID("SimpleInlineCompletionProvider")

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
      return InlineCompletionSuggestion.withFlow {
        suggestion.forEach { emit(it) }
      }
    }

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
      return event is InlineCompletionEvent.DocumentChange
    }
  }
}
