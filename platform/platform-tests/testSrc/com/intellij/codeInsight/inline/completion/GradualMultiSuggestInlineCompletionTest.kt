// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.impl.GradualMultiSuggestInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionBuilder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileTypes.PlainTextFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class GradualMultiSuggestInlineCompletionTest : InlineCompletionTestCase() {

  private lateinit var provider: GradualMultiSuggestInlineCompletionProvider

  private fun registerSuggestion(suggestionBuilder: suspend InlineCompletionSuggestionBuilder.() -> Unit) {
    provider = GradualMultiSuggestInlineCompletionProvider(suggestionBuilder)
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)
  }

  private suspend fun GradualMultiSuggestInlineCompletionProvider.assertComputed() {
    withContext(Dispatchers.EDT) {
      assertTrue(isComputed())
    }
  }

  private suspend fun GradualMultiSuggestInlineCompletionProvider.assertNotComputed() {
    withContext(Dispatchers.EDT) {
      assertFalse(isComputed())
    }
  }

  @Test
  fun `test switch non-empty variants while they are computed`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE, "value: <caret>, TODO")
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("First"))
        emit(InlineCompletionGrayTextElement("Second"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("Third"))
        emit(InlineCompletionGrayTextElement("Fourth"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("Fifth"))
        emit(InlineCompletionGrayTextElement("Sixth"))
      }
    }

    callInlineCompletion()
    provider.computeNextElement()

    assertSessionSnapshot(
      nonEmptyVariants = 1..3,
      activeIndex = 0,
      ExpectedVariant.inProgress("First"),
      ExpectedVariant.untouched(),
      ExpectedVariant.untouched()
    )

    assertInlineElements { gray("First") }

    nextVariant()
    assertInlineElements { gray("First") }
    prevVariant()
    assertInlineElements { gray("First") }
    prevVariant()
    assertInlineElements { gray("First") }

    assertSessionSnapshot(
      nonEmptyVariants = 1..3,
      activeIndex = 0,
      ExpectedVariant.inProgress("First"),
      ExpectedVariant.untouched(),
      ExpectedVariant.untouched()
    )

    provider.computeNextElements(number = 4)
    assertInlineElements { gray("First"); gray("Second") }

    nextVariant()
    assertInlineElements { gray("Third"); gray("Fourth") }

    nextVariant()
    assertInlineElements { gray("Fifth") }

    prevVariant()
    assertInlineElements { gray("Third"); gray("Fourth") }

    prevVariant()
    assertInlineElements { gray("First"); gray("Second") }

    prevVariant()
    assertInlineElements { gray("Fifth") }

    assertSessionSnapshot(
      nonEmptyVariants = 3..3,
      activeIndex = 2,
      ExpectedVariant.computed("First", "Second"),
      ExpectedVariant.computed("Third", "Fourth"),
      ExpectedVariant.inProgress("Fifth")
    )

    nextVariant()
    assertInlineElements { gray("First"); gray("Second") }

    provider.assertNotComputed()
    provider.computeNextElement()
    assertInlineElements { gray("First"); gray("Second") }

    prevVariant()
    assertInlineElements { gray("Fifth"); gray("Sixth") }

    provider.assertComputed()

    insert()
    assertInlineHidden()

    assertFileContent("value: FifthSixth<caret>, TODO")
  }

  @Test
  fun `test switch variants when there is only one variant`() = myFixture.testInlineCompletion {

    suspend fun InlineCompletionLifecycleTestDSL.invokeForNextPrev(
      block: suspend InlineCompletionLifecycleTestDSL.() -> Unit
    ) {
      nextVariant()
      block()
      prevVariant()
      block()
      prevVariant()
      block()
      nextVariant()
      block()
    }

    init(PlainTextFileType.INSTANCE, "start <caret>")
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("Only"))
        emit(InlineCompletionGrayTextElement("One"))
        emit(InlineCompletionGrayTextElement("Variant"))
      }
    }

    callInlineCompletion()
    assertInlineElements {  }
    provider.assertNotComputed()

    provider.computeNextElement()
    invokeForNextPrev {
      assertInlineElements { gray("Only") }
    }

    provider.computeNextElement()
    invokeForNextPrev {
      assertInlineElements { gray("Only"); gray("One") }
    }

    provider.computeNextElement()
    invokeForNextPrev {
      assertInlineElements { gray("Only"); gray("One"); gray("Variant") }
    }

    provider.assertComputed()

    assertSessionSnapshot(1..1, 0, ExpectedVariant.computed("Only", "One", "Variant"))
  }

  @Test
  fun `test switch variants when completion is hidden`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)

    suspend fun doTest() {
      assertInlineHidden()
      nextVariant()
      assertInlineHidden()
      prevVariant()
      assertInlineHidden()
      prevVariant()
      assertInlineHidden()
    }

    doTest()
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("Hello"))
      }
    }
    doTest()

    callInlineCompletion()
    provider.computeNextElement()
    delay()
    insert()
    doTest()

    assertFileContent("Hello<caret>")
  }

  @Test
  fun `test delay waits for all variants`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant {
        delay(50)
        emit(InlineCompletionGrayTextElement("First"))
      }
      variant {
        delay(75)
        emit(InlineCompletionGrayTextElement("Second"))
      }
      variant {
        delay(100)
        emit(InlineCompletionGrayTextElement("Third"))
      }
    }
    callInlineCompletion()
    provider.computeNextElements(3, await = false)
    provider.assertNotComputed()
    delay()
    provider.assertComputed()
  }

  @Test
  fun `test variant is automatically changed when the first variants are empty`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)

    registerSuggestion {
      repeat(5) {
        variant { delay(10) }
      }
      variant {
        emit(InlineCompletionGrayTextElement("1"))
        emit(InlineCompletionGrayTextElement("1"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("2"))
        emit(InlineCompletionGrayTextElement("2"))
      }
    }
    callInlineCompletion()
    provider.computeNextElement()
    provider.assertNotComputed()

    assertInlineRender("1")
    nextVariant()
    assertInlineRender("1")

    assertSessionSnapshot(
      nonEmptyVariants = 1..2,
      activeIndex = 5,
      *Array(5) { ExpectedVariant.empty() },
      ExpectedVariant.inProgress("1"),
      ExpectedVariant.untouched()
    )

    provider.computeNextElements(3)
    provider.assertComputed()

    assertInlineRender("11")

    nextVariant()
    assertInlineRender("22")
    nextVariant()
    assertInlineRender("11")
    prevVariant()
    assertInlineRender("22")

    assertSessionSnapshot(
      nonEmptyVariants = 2..2,
      activeIndex = 6,
      *Array(5) { ExpectedVariant.empty() },
      ExpectedVariant.computed("1", "1"),
      ExpectedVariant.computed("2", "2")
    )
  }

  @Test
  fun `test all variants are empty`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      repeat(20) {
        variant { }
      }
    }

    callInlineCompletion()
    delay()
    assertInlineHidden()
  }

  @Test
  fun `test further empty elements do not switch variants`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      repeat(2) {
        variant { emit(InlineCompletionGrayTextElement("Some variant $it")) }
      }
      repeat(3) {
        variant { }
      }
      repeat(2) {
        variant { emit(InlineCompletionGrayTextElement("Another variant $it")) }
      }
      repeat(3) {
        variant { }
      }
    }
    callInlineCompletion()
    provider.computeNextElements(2)

    assertInlineRender("Some variant 0")
    nextVariant()
    assertInlineRender("Some variant 1")
    nextVariant()
    assertInlineRender("Some variant 0")
    prevVariant()
    assertInlineRender("Some variant 1")

    assertSessionSnapshot(
      nonEmptyVariants = 2..7,
      activeIndex = 1,
      ExpectedVariant.computed("Some variant 0"),
      ExpectedVariant.computed("Some variant 1"),
      *Array(3) { ExpectedVariant.empty() },
      *Array(5) { ExpectedVariant.untouched() }
    )

    provider.computeNextElements(2)
    delay()

    assertInlineRender("Some variant 1")
    nextVariant()
    assertInlineRender("Another variant 0")
    nextVariant()
    assertInlineRender("Another variant 1")
    nextVariant()
    assertInlineRender("Some variant 0")
    prevVariant()
    assertInlineRender("Another variant 1")

    assertAllVariants("Another variant 1", "Some variant 0", "Some variant 1", "Another variant 0")

    insert()
    assertInlineHidden()
    assertFileContent("Another variant 1<caret>")
  }

  // TODO deprecated methods
  // TODO all builders
  // TODO test logs
  // TODO test UserDataHolder
}
