// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.impl.GradualMultiSuggestInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionBuilder
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.atomic.AtomicInteger

@RunWith(JUnit4::class)
internal class InlineCompletionSuggestionTest : InlineCompletionTestCase() {

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
    val variants = 20
    registerSuggestion {
      coroutineScope {
        val countdown = AtomicInteger(variants)
        repeat(variants) { iteration ->
          launch {
            countdown.decrementAndGet()
            while (countdown.get() != 0) {
              yield()
            }
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

  @Test
  fun `test variant builders`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    val key = Key.create<Int>("inline.completion.suggestion.test")
    val provider = TestInlineCompletionProvider { _ ->
      object : InlineCompletionSuggestion {
        override suspend fun getVariants(): List<InlineCompletionVariant> {
          return buildList {
            val data1 = UserDataHolderBase()
            val data2 = UserDataHolderBase()
            data1.putUserData(key, 1)
            data2.putUserData(key, 2)
            this += InlineCompletionVariant.build(data1) { data ->
              assertEquals(1, data.getUserData(key))
              emit(InlineCompletionGrayTextElement("first"))
              data.putUserData(key, 11)
            }
            this += InlineCompletionVariant.build(data2, flowOf(InlineCompletionGrayTextElement("second")))
            this += InlineCompletionVariant.build { data ->
              data.putUserData(key, 33)
              emit(InlineCompletionGrayTextElement("third"))
            }
            assertEquals(1, data1.getUserData(key))
            assertEquals(2, data2.getUserData(key))
          }
        }
      }
    }
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)
    callInlineCompletion()
    delay()

    assertSessionSnapshot(
      3..3,
      0,
      ExpectedVariant.computed("first"),
      ExpectedVariant.computed("second"),
      ExpectedVariant.computed("third")
    )
    val snapshot = withContext(Dispatchers.EDT) {
      assertNotNull(InlineCompletionSession.getOrNull(myFixture.editor)?.capture())
    }
    assertEquals(snapshot.variantsNumber, 3)
    val (variant1, variant2, variant3) = snapshot.variants
    assertEquals(11, variant1.data.getUserData(key))
    assertEquals(2, variant2.data.getUserData(key))
    assertEquals(33, variant3.data.getUserData(key))
  }

  private fun interface TestInlineCompletionProvider : InlineCompletionProvider {
    override val id: InlineCompletionProviderID
      get() = InlineCompletionProviderID("TEST")

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
      return event is InlineCompletionEvent.DirectCall || event is InlineCompletionEvent.DocumentChange
    }
  }
}
