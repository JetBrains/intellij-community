// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent.DirectCall
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent.DocumentChange
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.codeInsight.inline.completion.exception.InlineCompletionTestExceptions
import com.intellij.codeInsight.inline.completion.impl.GradualMultiSuggestInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.InsertedStateEvents
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.InvokedEvents
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.InvokedEvents.Outcome
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.logs.TEST_CHECK_STATE_AFTER_MLS
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionBuilder
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.reflect.KClass
import kotlin.test.assertNotEquals

private typealias Data = Map<String, Any>

@RunWith(JUnit4::class)
internal class FusInlineCompletionTest : InlineCompletionTestCase() {

  private lateinit var provider: GradualMultiSuggestInlineCompletionProvider

  private fun registerSuggestion(suggestionBuilder: suspend InlineCompletionSuggestionBuilder.() -> Unit) {
    provider = GradualMultiSuggestInlineCompletionProvider(suggestionBuilder)
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)
  }

  private fun getLogsState(block: suspend InlineCompletionLifecycleTestDSL.() -> Unit): LogsState {
    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      myFixture.testInlineCompletion(block = block)
    }
    return LogsState(events)
  }

  @Test
  fun `test no variants`() {
    val state = getLogsState {
      init(PlainTextFileType.INSTANCE)
      registerSuggestion {  }
      callInlineCompletion()
      delay()
      escape()
    }
    val invokedData = state.assertInvokedData()
    state.assertNoComputedData()
    assertInvokedData(invokedData, Outcome.NO_SUGGESTIONS, GradualMultiSuggestInlineCompletionProvider::class, DirectCall::class)
  }

  @Test
  fun `test variants with escaping`() {
    val state = getLogsState {
      init(PlainTextFileType.INSTANCE)
      registerSuggestion {
        variant {
          emit(InlineCompletionGrayTextElement("one"))
          emit(InlineCompletionGrayTextElement("two"))
        }
        variant {
          emit(InlineCompletionGrayTextElement("no \n"))
          emit(InlineCompletionGrayTextElement(" variants\n at all"))
        }
        variant {
          emit(InlineCompletionGrayTextElement("3"))
          emit(InlineCompletionGrayTextElement("4"))
        }
      }
      callInlineCompletion()
      provider.computeNextElements(6)
      delay()
      nextVariant()
      prevVariant()
      escape()
    }
    val (invokedData, computedData) = state.assertInvokedComputedData()
    assertRequestId(invokedData, computedData)
    assertInvokedData(invokedData, Outcome.SHOW, GradualMultiSuggestInlineCompletionProvider::class, DirectCall::class)
    assertComputedData(
      computedData,
      length = listOf(6, 21, 2),
      lines = listOf(1, 3, 1),
      FinishType.ESCAPE_PRESSED,
      typingDuringShow = 0,
      switchingVariants = 2
    )
  }

  @Test
  fun `test variants after accepting`() {
    val state = getLogsState {
      init(PlainTextFileType.INSTANCE, "<caret>next")
      registerSuggestion {
        variant {
          emit(InlineCompletionGrayTextElement("first"))
        }
        variant {
          emit(InlineCompletionGrayTextElement("1"))
          emit(InlineCompletionSkipTextElement("next"))
          emit(InlineCompletionGrayTextElement("2"))
        }
      }
      typeChar(' ')
      provider.computeNextElements(4)
      delay()
      nextVariant()
      insert()
    }
    val (invokedData, computedData) = state.assertInvokedComputedData()
    assertRequestId(invokedData, computedData)
    assertInvokedData(invokedData, Outcome.SHOW, GradualMultiSuggestInlineCompletionProvider::class, DocumentChange::class)
    assertComputedData(
      computedData,
      length = listOf(5, 6),
      lines = listOf(1, 1),
      FinishType.SELECTED,
      typingDuringShow = 0,
      switchingVariants = 1,
      selectedIndex = 1
    )
  }

  @Test
  fun `test not computed current variant`() {
    val state = getLogsState {
      init(PlainTextFileType.INSTANCE)
      registerSuggestion {
        variant {
          emit(InlineCompletionGrayTextElement("1"))
          emit(InlineCompletionGrayTextElement("2"))
        }
        variant {
          emit(InlineCompletionGrayTextElement("3"))
        }
      }
      callInlineCompletion()
      provider.computeNextElements(1)
      insert()
      assertFileContent("1<caret>")
      delay()
    }
    val (invokedData, computedData) = state.assertInvokedComputedData()
    assertRequestId(invokedData, computedData)
    assertInvokedData(invokedData, Outcome.CANCELED, GradualMultiSuggestInlineCompletionProvider::class, DirectCall::class)
    assertComputedData(
      computedData,
      length = listOf(1),
      lines = listOf(1),
      FinishType.SELECTED,
      typingDuringShow = 0,
      switchingVariants = 0,
      selectedIndex = 0
    )
  }

  @Test
  fun `test not computed next variant`() {
    val state = getLogsState {
      init(PlainTextFileType.INSTANCE)
      registerSuggestion {
        variant {
          emit(InlineCompletionGrayTextElement("1"))
          emit(InlineCompletionGrayTextElement("2"))
        }
        variant {
          emit(InlineCompletionGrayTextElement("3"))
          emit(InlineCompletionGrayTextElement("4"))
        }
      }
      callInlineCompletion()
      provider.computeNextElements(3)
      insert()
      assertFileContent("12<caret>")
      delay()
    }
    val (invokedData, computedData) = state.assertInvokedComputedData()
    assertRequestId(invokedData, computedData)
    assertInvokedData(invokedData, Outcome.CANCELED, GradualMultiSuggestInlineCompletionProvider::class, DirectCall::class)
    assertComputedData(
      computedData,
      length = listOf(2, 1),
      lines = listOf(1, 1),
      FinishType.SELECTED,
      typingDuringShow = 0,
      switchingVariants = 0,
      selectedIndex = 0
    )
  }

  @Test
  fun `test exception while computing`() {
    val state = getLogsState {
      init(PlainTextFileType.INSTANCE)
      registerSuggestion {
        variant {
          emit(InlineCompletionGrayTextElement("1"))
        }
        variant {
          emit(InlineCompletionGrayTextElement("2"))
          emit(InlineCompletionGrayTextElement("3"))
          withContext(Dispatchers.EDT) { }
          throw InlineCompletionTestExceptions.createExpectedTestException("expected error")
        }
        variant {
          emit(InlineCompletionGrayTextElement("4"))
        }
      }
      callInlineCompletion()
      provider.computeNextElements(2)
      nextVariant()
      provider.computeNextElements(2, await = false)
      delay()
    }
    val (invokedData, computedData) = state.assertInvokedComputedData()
    assertRequestId(invokedData, computedData)
    assertInvokedData(invokedData, Outcome.EXCEPTION, GradualMultiSuggestInlineCompletionProvider::class, DirectCall::class)
    assertComputedData(
      computedData,
      length = listOf(1, 2),
      lines = listOf(1, 1),
      FinishType.ERROR,
      typingDuringShow = 0,
      switchingVariants = 1
    )
  }

  @Test
  fun `test no invocation`() {
    val state = getLogsState {
      init(PlainTextFileType.INSTANCE)
      registerSuggestion {
        variant {
          emit(InlineCompletionGrayTextElement("1"))
        }
      }
      delay()
    }
    state.assertNoInvokedData()
    state.assertNoComputedData()
  }

  @Test
  fun `test all variants are empty`() {
    val state = getLogsState {
      init(PlainTextFileType.INSTANCE)
      registerSuggestion {
        repeat(10) {
          variant { }
        }
      }
      callInlineCompletion()
      delay()
    }
    val invokedData = state.assertInvokedData()
    state.assertNoComputedData()
    assertInvokedData(invokedData, Outcome.NO_SUGGESTIONS, GradualMultiSuggestInlineCompletionProvider::class, DirectCall::class)
  }

  @Test
  fun `test switching when only one variant is not empty`() {
    val state = getLogsState {
      init(PlainTextFileType.INSTANCE)
      registerSuggestion {
        repeat(5) { variant { } }
        variant {
          emit(InlineCompletionGrayTextElement("one\ntwo"))
        }
        repeat(5) { variant { } }
      }
      callInlineCompletion()
      provider.computeNextElements(1)
      delay()
      prevVariant()
      nextVariant()
      nextVariant()
      assertInlineRender("one\ntwo")
      insert()
    }
    val (invokedData, computedData) = state.assertInvokedComputedData()
    assertRequestId(invokedData, computedData)
    assertInvokedData(invokedData, Outcome.SHOW, GradualMultiSuggestInlineCompletionProvider::class, DirectCall::class)
    assertComputedData(
      computedData,
      length = listOf(0, 0, 0, 0, 0, 7),
      lines = listOf(0, 0, 0, 0, 0, 2),
      FinishType.SELECTED,
      typingDuringShow = 0,
      switchingVariants = 0,
      selectedIndex = 5
    )
  }

  @Test
  fun `test several events`() {
    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE)
        registerSuggestion {
          variant { emit(InlineCompletionGrayTextElement("one")) }
          variant { emit(InlineCompletionGrayTextElement("two")) }
        }
        callInlineCompletion()
        provider.computeNextElements(2)
        delay()
        escape()

        registerSuggestion {
          variant { emit(InlineCompletionGrayTextElement("1")) }
          variant { emit(InlineCompletionGrayTextElement("22")) }
          variant { emit(InlineCompletionGrayTextElement("333\n")) }
          variant { emit(InlineCompletionGrayTextElement("4444")) }
        }
        typeChar(' ')
        provider.computeNextElements(1)
        nextVariant() // nothing is switched
        provider.computeNextElements(2)
        nextVariant()
        nextVariant()
        nextVariant()
        nextVariant()
        insert()
        assertFileContent(" 22<caret>")
        delay()
      }
    }
    val allInvokedData = events.filter { it.group.isInlineCompletion() && it.event.id == InlineCompletionUsageTracker.INVOKED_EVENT_ID }
    val allComputedData = events.filter { it.group.isInlineCompletion() && it.event.id == InlineCompletionUsageTracker.SHOWN_EVENT_ID }
    assertEquals(2, allInvokedData.size)
    assertEquals(2, allComputedData.size)
    val (invokedData1, invokedData2) = allInvokedData.map { it.event.data }
    val (computedData1, computedData2) = allComputedData.map { it.event.data }
    assertRequestId(invokedData1, computedData1)
    assertRequestId(invokedData2, computedData2)
    assertNotEquals(invokedData1[InvokedEvents.REQUEST_ID], invokedData2[InvokedEvents.REQUEST_ID])

    assertInvokedData(invokedData1, Outcome.SHOW, GradualMultiSuggestInlineCompletionProvider::class, DirectCall::class)
    assertComputedData(
      computedData1,
      length = listOf(3, 3),
      lines = listOf(1, 1),
      FinishType.ESCAPE_PRESSED,
      typingDuringShow = 0,
      switchingVariants = 0
    )

    assertInvokedData(invokedData2, Outcome.CANCELED, GradualMultiSuggestInlineCompletionProvider::class, DocumentChange::class)
    assertComputedData(
      computedData2,
      length = listOf(1, 2, 4),
      lines = listOf(1, 1, 2),
      FinishType.SELECTED,
      typingDuringShow = 0,
      switchingVariants = 4,
      selectedIndex = 1
    )
  }

  @Test
  fun `test over typing`() {
    val state = getLogsState {
      myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE)
        registerSuggestion {
          variant {
            emit(InlineCompletionGrayTextElement("one "))
            emit(InlineCompletionGrayTextElement("two"))
          }
          variant { }
          variant {
            emit(InlineCompletionGrayTextElement("one "))
            emit(InlineCompletionGrayTextElement("three"))
          }
          variant {
            emit(InlineCompletionGrayTextElement("two "))
            emit(InlineCompletionGrayTextElement("one"))
          }
        }
        callInlineCompletion()
        provider.computeNextElements(6)
        delay()

        typeChars("one th")
        assertInlineRender("ree")
        insert()
        assertFileContent("one three<caret>")
      }
    }

    val (invokedData, computedData) = state.assertInvokedComputedData()
    assertRequestId(invokedData, computedData)
    assertInvokedData(invokedData, Outcome.SHOW, GradualMultiSuggestInlineCompletionProvider::class, DirectCall::class)
    assertComputedData(
      computedData,
      length = listOf(7, 0, 9, 7),
      lines = listOf(1, 0, 1, 1),
      FinishType.SELECTED,
      typingDuringShow = 6,
      switchingVariants = 0,
      selectedIndex = 2
    )
  }

  @Test
  fun `test inserted state logging after changes`() {
    val state = getLogsState {
      myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE)
        registerSuggestion {
          variant {
            emit(InlineCompletionGrayTextElement("one "))
            emit(InlineCompletionGrayTextElement("three"))
          }
        }
        callInlineCompletion()
        provider.computeNextElements(2)
        delay()

        typeChars("one th")
        assertInlineRender("ree")
        insert()
        backSpace()
        backSpace()
        typeChars("aa")
        assertFileContent("one thraa<caret>")
        delay(TEST_CHECK_STATE_AFTER_MLS * 3)
      }
    }
    val insertedStateData = state.assertInsertedStateData()
    assertInsertedStateData(
      insertedStateData,
      suggestionLength = 9,
      resultLength = 7,
      editDistance = 2,
      editDistanceNoAdd = 2,
      commonPrefixLength = 7,
      commonSuffixLength = 0,
    )
  }

  @Test
  fun `test inserted state logging after changes before suggestion`() {
    val state = getLogsState {
      myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE)
        typeChars("before ")
        registerSuggestion {
          variant {
            emit(InlineCompletionGrayTextElement("one "))
            emit(InlineCompletionGrayTextElement("three"))
          }
        }
        callInlineCompletion()
        provider.computeNextElements(2)
        delay()

        typeChars("one th")
        assertInlineRender("ree")
        insert()
        assertFileContent("before one three<caret>")

        navigateOnlyCaretTo(6)
        backSpace()
        backSpace()
        backSpace()
        assertFileContent("bef<caret> one three")
        delay(TEST_CHECK_STATE_AFTER_MLS * 3)
      }
    }
    val insertedStateData = state.assertInsertedStateData()
    assertInsertedStateData(
      insertedStateData,
      suggestionLength = 9,
      resultLength = 9,
      editDistance = 0,
      editDistanceNoAdd = 0,
      commonPrefixLength = 9,
      commonSuffixLength = 9,
    )
  }

  @Test
  fun `test inserted state logging without changes`() {
    val state = getLogsState {
      myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE)
        registerSuggestion {
          variant {
            emit(InlineCompletionGrayTextElement("one "))
            emit(InlineCompletionGrayTextElement("three"))
          }
        }
        callInlineCompletion()
        provider.computeNextElements(2)
        delay()

        typeChars("one th")
        assertInlineRender("ree")
        insert()
        assertFileContent("one three<caret>")
        delay(TEST_CHECK_STATE_AFTER_MLS * 3)
      }
    }
    val insertedStateData = state.assertInsertedStateData()
    assertInsertedStateData(
      insertedStateData,
      suggestionLength = 9,
      resultLength = 9,
      editDistance = 0,
      editDistanceNoAdd = 0,
      commonPrefixLength = 9,
      commonSuffixLength = 9,
    )
  }

  @Test
  fun `test inserted state logging after ctrl+z`() {
    val state = getLogsState {
      myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE)
        registerSuggestion {
          variant {
            emit(InlineCompletionGrayTextElement("one "))
            emit(InlineCompletionGrayTextElement("three"))
          }
        }
        callInlineCompletion()
        provider.computeNextElements(2)
        delay()

        typeChars("one th")
        assertInlineRender("ree")
        insert()
        assertFileContent("one three<caret>")

        callAction("\$Undo")
        assertFileContent("one th<caret>")
        delay(TEST_CHECK_STATE_AFTER_MLS * 3)
      }
    }
    val insertedStateData = state.assertInsertedStateData()
    assertInsertedStateData(
      insertedStateData,
      suggestionLength = 9,
      resultLength = 6,
      editDistance = 3,
      editDistanceNoAdd = 3,
      commonPrefixLength = 6,
      commonSuffixLength = 0,
    )
  }

  @Test
  fun `test inserted state logging after insertions inside suggestion`() {
    val state = getLogsState {
      myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE)
        registerSuggestion {
          variant {
            emit(InlineCompletionGrayTextElement("one "))
            emit(InlineCompletionGrayTextElement("three"))
          }
        }
        callInlineCompletion()
        provider.computeNextElements(2)
        delay()

        typeChars("one th")
        assertInlineRender("ree")
        insert()
        assertFileContent("one three<caret>")

        navigateOnlyCaretTo(7)
        typeChars("bb")
        assertFileContent("one thrbb<caret>ee")
        delay(TEST_CHECK_STATE_AFTER_MLS * 3)
      }
    }
    val insertedStateData = state.assertInsertedStateData()
    assertInsertedStateData(
      insertedStateData,
      suggestionLength = 9,
      resultLength = 11,
      editDistance = 2,
      editDistanceNoAdd = 0,
      commonPrefixLength = 7,
      commonSuffixLength = 2,
    )
  }

  private fun assertRequestId(invokedData: Data, computedData: Data) {
    assertEquals(invokedData[InvokedEvents.REQUEST_ID], computedData[ShownEvents.REQUEST_ID])
  }

  private fun assertInvokedData(
    invokedData: Data,
    outcome: Outcome,
    provider: KClass<out InlineCompletionProvider>,
    event: KClass<out InlineCompletionEvent>
  ) {
    assertEquals(outcome.toString(), invokedData[InvokedEvents.OUTCOME])
    assertEquals(provider.java.name, invokedData[InvokedEvents.PROVIDER])
    assertEquals(event.java.name, invokedData[InvokedEvents.EVENT])
  }

  private fun assertComputedData(
    computedData: Data,
    length: List<Int>,
    lines: List<Int>,
    finishType: FinishType,
    typingDuringShow: Int,
    switchingVariants: Int,
    selectedIndex: Int? = null
  ) {
    assertEquals(length, computedData[ShownEvents.LENGTH])
    assertEquals(lines, computedData[ShownEvents.LINES])
    assertEquals(finishType.toString(), computedData[ShownEvents.FINISH_TYPE])
    assertEquals(typingDuringShow, computedData[ShownEvents.LENGTH_CHANGE_DURING_SHOW])
    assertEquals(switchingVariants, computedData[ShownEvents.EXPLICIT_SWITCHING_VARIANTS_TIMES])
    assertEquals(selectedIndex, computedData[ShownEvents.SELECTED_INDEX])
  }

  private fun assertInsertedStateData(
    data: Data,
    suggestionLength: Int,
    resultLength: Int,
    editDistance: Int,
    editDistanceNoAdd: Int,
    commonPrefixLength: Int,
    commonSuffixLength: Int,
  ) {
    assertEquals(suggestionLength, data[InsertedStateEvents.SUGGESTION_LENGTH])
    assertEquals(resultLength, data[InsertedStateEvents.RESULT_LENGTH])
    assertEquals(TEST_CHECK_STATE_AFTER_MLS, data[EventFields.DurationMs])
    assertEquals(editDistance, data[InsertedStateEvents.EDIT_DISTANCE])
    assertEquals(editDistanceNoAdd, data[InsertedStateEvents.EDIT_DISTANCE_NO_ADD])
    assertEquals(commonPrefixLength, data[InsertedStateEvents.COMMON_PREFIX_LENGTH])
    assertEquals(commonSuffixLength, data[InsertedStateEvents.COMMON_SUFFIX_LENGTH])
  }

  private class LogsState(private val events: List<LogEvent>) {

    private fun getData(id: String): Data? {
      val event = events.firstOrNull { it.group.isInlineCompletion() && it.event.id == id } ?: return null
      return event.event.data
    }

    private fun getInvokedData(): Data? = getData(InlineCompletionUsageTracker.INVOKED_EVENT_ID)

    private fun getComputedData(): Data? = getData(InlineCompletionUsageTracker.SHOWN_EVENT_ID)

    private fun getInsertedStateData(): Data? = getData(InlineCompletionUsageTracker.INSERTED_STATE_EVENT_ID)

    fun assertInvokedData(): Data = getInvokedData().also { assertNotNull(it) }!!

    fun assertComputedData(): Data = getComputedData().also { assertNotNull(it) }!!

    fun assertInsertedStateData(): Data = getInsertedStateData().also { assertNotNull(it) }!!

    fun assertNoInvokedData(): Unit = assertNull(getInvokedData())

    fun assertNoComputedData(): Unit = assertNull(getComputedData())

    fun assertInvokedComputedData(): Pair<Data, Data> = assertInvokedData() to assertComputedData()
  }

  private operator fun Data.get(key: EventField<*>): Any? = get(key.name)

  companion object {
    private fun LogEventGroup.isInlineCompletion(): Boolean = id == InlineCompletionUsageTracker.group.id
  }
}
