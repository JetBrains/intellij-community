// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.impl.GradualMultiSuggestInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionBuilder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileTypes.PlainTextFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class InlineCompletionSuggestionBuilderTest : InlineCompletionTestCase() {

  private lateinit var provider: GradualMultiSuggestInlineCompletionProvider

  private fun registerSuggestion(suggestionBuilder: suspend InlineCompletionSuggestionBuilder.() -> Unit) {
    provider = GradualMultiSuggestInlineCompletionProvider(suggestionBuilder)
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)
  }

  private suspend fun InlineCompletionLifecycleTestDSL.collectVariants(size: Int): Set<String> {
    return buildSet {
      repeat(size) {
        add(assertContextExists().textToInsert())
        nextVariant()
      }
    }
  }

  @Test
  fun `test nested variants creation is forbidden`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("1"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("2"))
        variant {
          emit(InlineCompletionGrayTextElement("3"))
        }
      }
    }

    callInlineCompletion()
    provider.computeNextElements(3, await = false)
    delay()
    assertInlineHidden() // exception occurred, some problems with detecting exceptions occurred inside Dispatchers.Default
  }

  @Test
  fun `test concurrent variant generation`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    val variants = 1500
    registerSuggestion {
      coroutineScope {
        repeat(variants) { iteration ->
          launch {
            variant {
              emit(InlineCompletionGrayTextElement(iteration.toString()))
            }
          }
        }
      }
    }

    callInlineCompletion()
    provider.computeNextElements(variants, await = false)
    delay()
    val context = assertContextExists()
    assertTrue(context.textToInsert().isNotEmpty())
    val actualVariantsNumber = withContext(Dispatchers.EDT) {
      InlineCompletionSession.getOrNull(fixture.editor)?.capture()?.variantsNumber
    }
    assertEquals(variants, actualVariantsNumber)

    val allVariants = collectVariants(variants)
    assertEquals(List(variants) { it.toString() }.sorted(), allVariants.sorted())
  }
}
