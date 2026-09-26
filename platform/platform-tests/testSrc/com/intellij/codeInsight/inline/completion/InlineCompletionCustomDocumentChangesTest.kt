// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager.UpdateResult
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class InlineCompletionCustomDocumentChangesTest : InlineCompletionTestCase() {

  @Test
  fun `test partial acceptance`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    val provider = Provider("12345678")
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)

    callInlineCompletion()
    delay()
    assertInlineRender("12345678")
    typeChar('1')
    assertInlineRender("2345678")

    sendAcceptSymbolEvent()
    assertInlineRender("345678")
    sendAcceptSymbolEvent()
    typeChar('4')
    assertInlineRender("5678")
    insert()
    assertFileContent("12345678<caret>")
    assertInlineHidden()
  }

  private suspend fun sendAcceptSymbolEvent() {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        InlineCompletion.getHandlerOrNull(myFixture.editor)!!.invokeEvent(AcceptSymbolEvent())
      }
    }
  }

  private inner class AcceptSymbolEvent : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest {
      return InlineCompletionRequest(
        this,
        myFixture.file,
        myFixture.editor,
        myFixture.editor.document,
        myFixture.caretOffset,
        myFixture.caretOffset + 1
      )
    }
  }

  private inner class AcceptSymbolSuggestionUpdateManager : InlineCompletionSuggestionUpdateManager.Default() {
    override fun onCustomEvent(event: InlineCompletionEvent, variant: InlineCompletionVariant.Snapshot): UpdateResult {
      return when (event) {
        is AcceptSymbolEvent -> {
          val element = variant.elements.singleOrNull()
          assertNotNull(element)
          element!!

          val editor = myFixture.editor
          InlineCompletion.getHandlerOrNull(editor)!!.withIgnoringDocumentChanges {
            application.runWriteAction {
              CommandProcessor.getInstance().executeCommand(
                myFixture.project,
                { editor.document.insertString(myFixture.caretOffset, element.text.first().toString()) },
                null,
                null,
                myFixture.editor.document
              )
              editor.caretModel.moveToOffset(myFixture.caretOffset + 1)
              PsiDocumentManager.getInstance(myFixture.file.project).commitDocument(editor.document)
            }
          }
          UpdateResult.Changed(
            variant.copy(listOf(InlineCompletionGrayTextElement(element.text.drop(1))))
          )
        }
        else -> UpdateResult.Same
      }
    }
  }

  private inner class Provider(private val text: String) : InlineCompletionProvider {
    override val id: InlineCompletionProviderID = InlineCompletionProviderID("Provider")

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
      return event is InlineCompletionEvent.DirectCall
    }

    override val suggestionUpdateManager = AcceptSymbolSuggestionUpdateManager()

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
      return InlineCompletionSingleSuggestion.build {
        emit(InlineCompletionGrayTextElement(text))
      }
    }
  }
}
