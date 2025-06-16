// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.impl.SimpleInlineCompletionProvider
import com.intellij.openapi.fileTypes.PlainTextFileType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class RTLInlineCompletionTest : InlineCompletionTestCase() {

  private fun registerSuggestion(vararg suggestion: InlineCompletionElement) {
    InlineCompletionHandler.registerTestHandler(Provider(suggestion.toList()), testRootDisposable)
  }

  @Test
  fun `test accept RTL inline suggestion at the start position`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE, "<caret> مرحبا، كيف حالك")
    caret {
      // Forcing RTL
      moveRight(); moveRight()
    }
    registerSuggestion(InlineCompletionGrayTextElement("اليوم؟"))
    callInlineCompletion()
    delay()
    assertInlineRender("اليوم؟")
    typeChar('ا')
    assertInlineRender("ليوم؟")
    insertWithTab()
    assertInlineHidden()
    assertFileContent(" مرحبا، كيف حالكاليوم؟<caret>") // it's actually " <caret>arabic". It's just rendered this way in IJ.
  }

  @Test
  fun `test accept RTL inline suggestion inside RTL part`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE, "<caret> مرحبا، كيف حالك")
    caret {
      // RTL position after the first (last) letter
      moveRight(); moveRight(); moveRight()
    }
    registerSuggestion(InlineCompletionGrayTextElement("اليوم؟"))
    callInlineCompletion()
    delay()
    assertInlineRender("اليوم؟")
    insertWithTab()
    assertFileContent(" مرحبا، كيف حالاليوم؟<caret>ك")
  }

  private class Provider(suggestion: List<InlineCompletionElement>) : SimpleInlineCompletionProvider(suggestion) {
    override fun isEnabled(event: InlineCompletionEvent): Boolean {
      return super.isEnabled(event) || event is InlineCompletionEvent.DirectCall
    }
  }
}
