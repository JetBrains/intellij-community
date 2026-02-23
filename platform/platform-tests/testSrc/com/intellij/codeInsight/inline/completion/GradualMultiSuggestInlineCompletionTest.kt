// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.impl.GradualMultiSuggestInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionBuilder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.atomic.AtomicBoolean

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
    val midpointSync = AtomicBoolean(false) // to avoid flakiness
    registerSuggestion {
      repeat(2) {
        variant { _ -> emit(InlineCompletionGrayTextElement("Some variant $it")) }
      }
      repeat(3) {
        variant { }
      }
      repeat(2) {
        variant { _ ->
          midpointSync.set(true)
          emit(InlineCompletionGrayTextElement("Another variant $it"))
        }
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

    while (!midpointSync.get()) {
      yield()
    }

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

  @Test
  fun `test data transportation`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    val key1 = Key.create<Int>("inline.completion.test.key.one")
    val key2 = Key.create<Int>("inline.completion.test.key.two")
    val middleSync = AtomicBoolean(false)
    registerSuggestion {
      variant { }
      variant { data ->
        data.putUserData(key1, 1)
        emit(InlineCompletionGrayTextElement("First"))
        data.putUserData(key1, 2)
        emit(InlineCompletionGrayTextElement("Second"))
        data.putUserData(key1, 3)
      }
      variant { data -> data.putUserData(key1, -1) } // messing
      variant { data ->
        data.putUserData(key2, 1)
        middleSync.set(true)
        emit(InlineCompletionGrayTextElement("Third"))
        data.putUserData(key2, 2)
        emit(InlineCompletionGrayTextElement("Fourth"))
        data.putUserData(key2, 3)
      }
      variant { }
    }

    suspend fun assertDataValue(variantIndex: Int, expectedValue: Int?, key: Key<Int>) {
      withContext(Dispatchers.EDT) {
        val data = getVariant(variantIndex).data
        assertEquals(expectedValue, data.getUserData(key))
        // Only one key at a time
        assertNull(data.getUserData(if (key === key1) key2 else key1))
      }
    }

    suspend fun assertEmpty(vararg variantIndices: Int) {
      variantIndices.forEach { assertDataValue(it, null, key1) }
    }

    suspend fun assertContext(variantIndex: Int) {
      withContext(Dispatchers.EDT) {
        val data = getVariant(variantIndex).data
        val context = assertContextExists()
        assertEquals(data.getUserData(key1), context.getUserData(key1))
        assertEquals(data.getUserData(key2), context.getUserData(key2))
      }
    }

    callInlineCompletion()
    provider.computeNextElement()
    assertEmpty(0, 2, 3, 4)
    assertDataValue(1, 2, key1) // The provider reaches the next element and stops
    assertContext(1)

    provider.computeNextElement()
    while (!middleSync.get()) {
      yield()
    }
    assertEmpty(0, 4)
    assertDataValue(1, 3, key1)
    assertDataValue(3, 1, key2)
    assertDataValue(2, -1, key1)
    assertContext(1)

    provider.computeNextElement()
    assertEmpty(0, 4)
    assertDataValue(1, 3, key1)
    assertDataValue(3, 2, key2)
    assertDataValue(2, -1, key1)
    assertContext(1)

    nextVariant()
    assertEmpty(0, 4)
    assertDataValue(1, 3, key1)
    assertDataValue(3, 2, key2)
    assertContext(3)

    prevVariant()
    assertContext(1)

    provider.computeNextElement()
    delay()
    assertEmpty(0, 4)
    assertDataValue(1, 3, key1)
    assertDataValue(3, 3, key2)
    assertContext(1)
    nextVariant()
    prevVariant()
    prevVariant()
    assertContext(3)
    prevVariant()
    assertContext(1)
    nextVariant()
    nextVariant()
    nextVariant()
    prevVariant()
    assertContext(1)

    assertEmpty(0, 4)
    assertDataValue(1, 3, key1)
    assertDataValue(2, -1, key1)
    assertDataValue(3, 3, key2)
  }

  @Test
  fun `test shared data between variants`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    val key = Key.create<Int>("inline.completion.test.shared.key")
    val data = UserDataHolderBase()
    registerSuggestion {
      variant(data) { currentData ->
        emit(InlineCompletionGrayTextElement("First"))
        currentData.putUserData(key, 1)
        emit(InlineCompletionGrayTextElement("Second"))
        emit(InlineCompletionGrayTextElement("Third"))
      }
      variant(data) { currentData ->
        emit(InlineCompletionGrayTextElement("Fourth"))
        currentData.putUserData(key, -1)
        emit(InlineCompletionGrayTextElement("Fifth"))
      }
    }

    suspend fun assertData(value: Int?) {
      withContext(Dispatchers.EDT) {
        assertEquals(value, getVariant(0).data.getUserData(key))
        assertEquals(value, getVariant(1).data.getUserData(key))
        assertEquals(value, data.getUserData(key))
        assertEquals(value, assertContextExists().getUserData(key))
      }
    }

    callInlineCompletion()
    provider.computeNextElements(2)
    assertData(1)
    provider.computeNextElements(3)
    delay()
    assertData(-1)
    nextVariant()
    assertData(-1)
    nextVariant()
    assertData(-1)
    prevVariant()
    assertData(-1)
  }

  @Test
  fun `test lookup event when variants are not ready do not clear session`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("variant"))
      }
    }
    callInlineCompletion()
    fillLookup("1", "2")
    createLookup()
    pickLookupElement("1")
    hideLookup()
    provider.computeNextElements(1, await = false)
    delay()
    assertInlineRender("variant")
  }
}
