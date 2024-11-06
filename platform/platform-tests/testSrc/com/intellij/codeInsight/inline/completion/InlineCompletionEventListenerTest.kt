// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.InlineCompletionEventListenerTest.EventAsserter.Companion.change
import com.intellij.codeInsight.inline.completion.InlineCompletionEventListenerTest.EventAsserter.Companion.completion
import com.intellij.codeInsight.inline.completion.InlineCompletionEventListenerTest.EventAsserter.Companion.computed
import com.intellij.codeInsight.inline.completion.InlineCompletionEventListenerTest.EventAsserter.Companion.empty
import com.intellij.codeInsight.inline.completion.InlineCompletionEventListenerTest.EventAsserter.Companion.hide
import com.intellij.codeInsight.inline.completion.InlineCompletionEventListenerTest.EventAsserter.Companion.invalidated
import com.intellij.codeInsight.inline.completion.InlineCompletionEventListenerTest.EventAsserter.Companion.noVariants
import com.intellij.codeInsight.inline.completion.InlineCompletionEventListenerTest.EventAsserter.Companion.request
import com.intellij.codeInsight.inline.completion.InlineCompletionEventListenerTest.EventAsserter.Companion.show
import com.intellij.codeInsight.inline.completion.InlineCompletionEventListenerTest.EventAsserter.Companion.variantComputed
import com.intellij.codeInsight.inline.completion.InlineCompletionEventListenerTest.EventAsserter.Companion.variantSwitched
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.codeInsight.inline.completion.impl.GradualMultiSuggestInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.suggestion.*
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

@RunWith(JUnit4::class)
internal class InlineCompletionEventListenerTest : InlineCompletionTestCase() {

  private lateinit var provider: GradualMultiSuggestInlineCompletionProvider

  private fun testListener(test: suspend InlineCompletionLifecycleTestDSL.() -> Unit) {
    myFixture.testInlineCompletion {
      val listener = TestEventListener()
      withContext(currentCoroutineContext() + listener) {
        init(PlainTextFileType.INSTANCE)
        InlineCompletion.getHandlerOrNull(myFixture.editor)!!.addEventListener(listener)
        test()
        listener.expect() // no events stayed unprocessed
      }
    }
  }

  private suspend fun expectEvents(vararg assertions: EventAsserter) {
    val listener = currentCoroutineContext()[TestEventListener]
    listener!!.expect(*assertions)
  }

  private fun registerSuggestion(suggestionBuilder: suspend InlineCompletionSuggestionBuilder.() -> Unit) {
    provider = GradualMultiSuggestInlineCompletionProvider(suggestionBuilder)
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)
  }

  @Test
  fun `test no suggestions`() = testListener {
    registerSuggestion { }
    callInlineCompletion()
    delay()
    expectEvents(
      request(InlineCompletionEvent.DirectCall::class, GradualMultiSuggestInlineCompletionProvider::class),
      noVariants(),
      hide(FinishType.EMPTY, false),
      completion(null, true)
    )
  }

  @Test
  fun `test all variants are empty`() = testListener {
    registerSuggestion {
      variant { }
      variant { }
    }
    typeChar('x')
    delay()
    expectEvents(
      request(InlineCompletionEvent.DocumentChange::class, GradualMultiSuggestInlineCompletionProvider::class),
      empty(0),
      variantComputed(0),
      variantSwitched(0, 1, false),
      empty(1),
      variantComputed(1),
      noVariants(),
      hide(FinishType.EMPTY, false),
      completion(null, true)
    )
  }

  @Test
  fun `test switching variants`() = testListener {
    init(PlainTextFileType.INSTANCE, "<caret>three")
    registerSuggestion {
      variant { }
      variant {
        emit(InlineCompletionGrayTextElement("one"))
        emit(InlineCompletionGrayTextElement("two"))
        emit(InlineCompletionSkipTextElement("three"))
      }
      variant { }
      variant {
        emit(InlineCompletionGrayTextElement("1"))
        emit(InlineCompletionSkipTextElement("2"))
      }
      variant {
        emit(InlineCompletionSkipTextElement("3"))
      }
      variant { }
      variant { }
    }
    callInlineCompletion()
    provider.computeNextElements(4)
    expectEvents(
      request(InlineCompletionEvent.DirectCall::class, GradualMultiSuggestInlineCompletionProvider::class),
      empty(0),
      variantComputed(0),
      variantSwitched(0, 1, false),
      computed(1, "one", 0), show(1, "one", 0),
      computed(1, "two", 1), show(1, "two", 1),
      computed(1, "three", 2), show(1, "three", 2),
      variantComputed(1),
      empty(2),
      variantComputed(2),
      computed(3, "1", 0)
    )

    nextVariant()
    expectEvents(
      variantSwitched(1, 3, true),
      show(3, "1", 0)
    )
    nextVariant()
    expectEvents(
      variantSwitched(3, 1, true),
      show(1, "one", 0), show(1, "two", 1), show(1, "three", 2)
    )
    prevVariant()
    expectEvents(
      variantSwitched(1, 3, true),
      show(3, "1", 0)
    )
    provider.computeNextElements(2)
    delay()

    expectEvents(
      computed(3, "2", 1), show(3, "2", 1),
      variantComputed(3),
      computed(4, "3", 0),
      variantComputed(4),
      empty(5),
      variantComputed(5),
      empty(6),
      variantComputed(6),
      completion(null, true)
    )

    nextVariant()
    expectEvents(
      variantSwitched(3, 4, true),
      show(4, "3", 0)
    )
    nextVariant()
    expectEvents(
      variantSwitched(4, 1, true),
      show(1, "one", 0), show(1, "two", 1), show(1, "three", 2)
    )

    escape()
    expectEvents(
      hide(FinishType.ESCAPE_PRESSED, true)
    )
    assertInlineHidden()
  }

  @Test
  fun `test exception while providing variants`() = testListener {
    registerSuggestion {
      variant { emit(InlineCompletionGrayTextElement("value")) }
      variant { }
      throw IllegalStateException("expected exception")
      @Suppress("UNREACHABLE_CODE")
      variant { }
    }
    callInlineCompletion()
    delay()

    // Nothing was computed
    expectEvents(
      request(InlineCompletionEvent.DirectCall::class, GradualMultiSuggestInlineCompletionProvider::class),
      hide(FinishType.ERROR, false),
      completion(IllegalStateException::class, true)
    )
  }

  @Test
  fun `test exception while computing variants`() = testListener {
    registerSuggestion {
      variant { }
      variant { emit(InlineCompletionGrayTextElement("press X")) }
      variant { throw IllegalArgumentException("expected") }
      variant { emit(InlineCompletionGrayTextElement("press F")) }
    }
    callInlineCompletion()
    provider.computeNextElements(2, await = false)
    delay()

    expectEvents(
      request(InlineCompletionEvent.DirectCall::class, GradualMultiSuggestInlineCompletionProvider::class),
      empty(0),
      variantComputed(0),
      variantSwitched(0, 1, false),
      computed(1, "press X", 0), show(1, "press X", 0),
      variantComputed(1),
      hide(FinishType.ERROR, true),
      completion(IllegalArgumentException::class, true)
    )
  }

  @Test
  fun `test insert a proposal`() = testListener {
    registerSuggestion {
      variant { emit(InlineCompletionGrayTextElement("no")) }
      variant { emit(InlineCompletionGrayTextElement("yes")) }
    }
    callInlineCompletion()
    provider.computeNextElements(2)
    delay()
    nextVariant()
    insert()
    assertInlineHidden()
    assertFileContent("yes<caret>")

    expectEvents(
      request(InlineCompletionEvent.DirectCall::class, GradualMultiSuggestInlineCompletionProvider::class),
      computed(0, "no", 0), show(0, "no", 0),
      variantComputed(0),
      computed(1, "yes", 0),
      variantComputed(1),
      completion(null, true),
      variantSwitched(0, 1, true),
      show(1, "yes", 0),
      EventAsserter.insert(),
      hide(FinishType.SELECTED, true),
      EventAsserter.afterInsert(),
    )
  }

  @Test
  fun `test over typing`() = testListener {
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("fun "))
        emit(InlineCompletionGrayTextElement("main()"))
      }
      variant { }
      variant { emit(InlineCompletionGrayTextElement("foo bar")) }
      variant { emit(InlineCompletionGrayTextElement("fun start()")) }
      variant { emit(InlineCompletionGrayTextElement("fun")) }
    }
    callInlineCompletion()
    provider.computeNextElements(5)
    delay()

    expectEvents(
      request(InlineCompletionEvent.DirectCall::class, GradualMultiSuggestInlineCompletionProvider::class),
      computed(0, "fun ", 0), show(0, "fun ", 0),
      computed(0, "main()", 1), show(0, "main()", 1),
      variantComputed(0),
      empty(1),
      variantComputed(1),
      computed(2, "foo bar", 0),
      variantComputed(2),
      computed(3, "fun start()", 0),
      variantComputed(3),
      computed(4, "fun", 0),
      variantComputed(4),
      completion(null, true)
    )

    typeChar('f')
    expectEvents(
      change(0, 1), change(2, 1), change(3, 1), change(4, 1),
      show(0, "un ", 0),
      show(0, "main()", 1)
    )

    nextVariant()
    expectEvents(
      variantSwitched(0, 2, true),
      show(2, "oo bar", 0)
    )

    typeChar('u')
    expectEvents(
      change(0, 1), invalidated(2), change(3, 1), change(4, 1),
      variantSwitched(2, 3, false),
      show(3, "n start()", 0)
    )

    nextVariant()
    expectEvents(
      variantSwitched(3, 4, true),
      show(4, "n", 0)
    )
    nextVariant()
    expectEvents(
      variantSwitched(4, 0, true),
      show(0, "n ", 0), show(0, "main()", 1)
    )

    typeChar('n')
    expectEvents(
      change(0, 1), change(3, 1), invalidated(4),
      show(0, " ", 0), show(0, "main()", 1),
    )

    typeChar(' ')
    expectEvents(
      change(0, 1), change(3, 1),
      show(0, "main()", 0)
    )

    nextVariant()
    expectEvents(
      variantSwitched(0, 3, true),
      show(3, "start()", 0)
    )

    backSpace()
    expectEvents(
      hide(FinishType.BACKSPACE_PRESSED, true)
    )
  }

  @Test
  fun `test computations were cancelled`() = testListener {
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("simple"))
        emit(InlineCompletionGrayTextElement("another"))
      }
    }
    callInlineCompletion()
    provider.computeNextElement()
    expectEvents(
      request(InlineCompletionEvent.DirectCall::class, GradualMultiSuggestInlineCompletionProvider::class),
      computed(0, "simple", 0), show(0, "simple", 0)
    )

    InlineCompletion.getHandlerOrNull(fixture.editor)!!.cancel(FinishType.EDITOR_REMOVED)
    delay()
    expectEvents(
      hide(FinishType.EDITOR_REMOVED, true),
      completion(CancellationException::class, false)
    )
  }

  @Test
  fun `test type completely variant`() = testListener {
    registerSuggestion {
      variant { emit(InlineCompletionGrayTextElement("acd")) }
      variant { emit(InlineCompletionGrayTextElement("abc")) }
      variant { emit(InlineCompletionGrayTextElement("abcde")) }
    }
    callInlineCompletion()
    provider.computeNextElements(3)
    delay()
    InlineCompletionHandler.unRegisterTestHandler()

    expectEvents(
      request(InlineCompletionEvent.DirectCall::class, GradualMultiSuggestInlineCompletionProvider::class),
      computed(0, "acd", 0), show(0, "acd", 0),
      variantComputed(0),
      computed(1, "abc", 0),
      variantComputed(1),
      computed(2, "abcde", 0),
      variantComputed(2),
      completion(null, true)
    )

    nextVariant()
    expectEvents(
      variantSwitched(0, 1, true),
      show(1, "abc", 0)
    )
    typeChars("ab")
    expectEvents(
      change(0, 1), change(1, 1), change(2, 1),
      show(1, "bc", 0),
      invalidated(0), change(1, 1), change(2, 1),
      show(1, "c", 0)
    )

    typeChar('c')
    assertInlineHidden()
    expectEvents(
      change(1, 1), change(2, 1),
      hide(FinishType.TYPED, false)
    )
  }
  
  @Test
  fun `test events for custom event handling`() = testListener { 
    val updateManager = object : InlineCompletionSuggestionUpdateManager.Adapter {
      override fun onLookupEvent(
        event: InlineCompletionEvent.InlineLookupEvent,
        variant: InlineCompletionVariant.Snapshot
      ): InlineCompletionSuggestionUpdateManager.UpdateResult {
        val itemString = event.event.item?.lookupString ?: return InlineCompletionSuggestionUpdateManager.UpdateResult.Same
        if (event !is InlineCompletionEvent.LookupChange || !variant.isActive) {
          return InlineCompletionSuggestionUpdateManager.UpdateResult.Same
        }
        val snapshot = variant.copy(variant.elements.dropLast(1) + InlineCompletionGrayTextElement(itemString))
        return InlineCompletionSuggestionUpdateManager.UpdateResult.Changed(snapshot)
      }
    }
    val provider = object : InlineCompletionProvider {
      override val id: InlineCompletionProviderID = InlineCompletionProviderID("TEST")

      override val suggestionUpdateManager = updateManager

      override fun isEnabled(event: InlineCompletionEvent): Boolean {
        return event is InlineCompletionEvent.DirectCall || event is InlineCompletionEvent.DocumentChange
      }

      override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
        return InlineCompletionSuggestion {
          variant {
            emit(InlineCompletionGrayTextElement("first: "))
            emit(InlineCompletionGrayTextElement("some value"))
          }
          variant {
            emit(InlineCompletionGrayTextElement("second: "))
            emit(InlineCompletionGrayTextElement("another value"))
          }
        }
      }
    }
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)

    callInlineCompletion()
    delay()
    expectEvents(
      request(InlineCompletionEvent.DirectCall::class, InlineCompletionProvider::class),
      computed(0, "first: ", 0), show(0, "first: ", 0),
      computed(0, "some value", 1), show(0, "some value", 1),
      variantComputed(0),
      computed(1, "second: ", 0), computed(1, "another value", 1),
      variantComputed(1),
      completion(null, true)
    )

    fillLookup("love", "the", "world!")
    createLookup()
    assertInlineRender("first: love")
    expectEvents(
      change(0, 6),
      show(0, "first: ", 0), show(0, "love", 1)
    )

    nextVariant()
    expectEvents(
      variantSwitched(0, 1, true),
      show(1, "second: ", 0), show(1, "another value", 1)
    )
    pickLookupElement("world!")
    assertInlineRender("second: world!")
    expectEvents(
      change(1, 7),
      show(1, "second: ", 0), show(1, "world!", 1)
    )

    nextVariant()
    assertInlineRender("first: love")
    expectEvents(
      variantSwitched(1, 0, true),
      show(0, "first: ", 0), show(0, "love", 1)
    )
    escape()
    expectEvents(
      hide(FinishType.ESCAPE_PRESSED, true)
    )
  }

  private class TestEventListener : AbstractCoroutineContextElement(Key), InlineCompletionEventListener {

    private val lastEvents = mutableListOf<InlineCompletionEventType>()

    override fun on(event: InlineCompletionEventType) {
      ThreadingAssertions.assertEventDispatchThread()
      if (event !is InlineCompletionEventType.SuggestionInitialized) { // Will do that later :)
        lastEvents += event
      }
    }

    suspend fun expect(vararg asserters: EventAsserter) {
      withContext(Dispatchers.EDT) {
        for ((event, asserter) in lastEvents zip asserters) {
          asserter.assert(event)
        }
        if (lastEvents.size < asserters.size) {
          fail("Expected ${asserters.drop(lastEvents.size).map { it.name }} events.")
        }
        if (lastEvents.size > asserters.size) {
          fail("Not expected: ${lastEvents.drop(asserters.size).map { it::class.simpleName }}")
        }
        lastEvents.clear()
      }
    }

    companion object Key : CoroutineContext.Key<TestEventListener>
  }

  private interface EventAsserter {

    val name: String?

    fun assert(event: InlineCompletionEventType)

    companion object {

      private fun eventAsserter(name: String?, assertion: (InlineCompletionEventType) -> Unit): EventAsserter = object : EventAsserter {
        override val name: String? = name

        override fun assert(event: InlineCompletionEventType) = assertion(event)
      }

      private inline fun <reified T : InlineCompletionEventType> assert(crossinline assertion: (event: T) -> Unit): EventAsserter {
        return eventAsserter(T::class.simpleName) { event ->
          assertInstanceOf(event, T::class.java)
          try {
            assertion(event as T)
          } catch (e: AssertionError) {
            throw AssertionError("Happened while expecting ${T::class.simpleName}", e)
          }
        }
      }

      fun request(
        request: KClass<out InlineCompletionEvent>,
        provider: KClass<out InlineCompletionProvider>
      ) = assert<InlineCompletionEventType.Request> { event ->
        assertEquals(request, event.request.event::class)
        assertTrue(provider.java.isAssignableFrom(event.provider))
      }

      fun noVariants() = assert<InlineCompletionEventType.NoVariants> { }

      fun completion(cause: KClass<out Throwable>?, isActive: Boolean) = assert<InlineCompletionEventType.Completion> { event ->
        if (cause == null) {
          assertNull(event.cause)
        }
        else {
          assertNotNull(event.cause)
          assertInstanceOf(event.cause, cause.java)
        }
        assertEquals(isActive, event.isActive)
      }

      fun insert() = assert<InlineCompletionEventType.Insert> { }

      fun afterInsert() = assert<InlineCompletionEventType.AfterInsert> { }

      fun variantSwitched(
        fromVariantIndex: Int,
        toVariantIndex: Int,
        explicit: Boolean
      ) = assert<InlineCompletionEventType.VariantSwitched> { event ->
        assertEquals(fromVariantIndex, event.fromVariantIndex)
        assertEquals(toVariantIndex, event.toVariantIndex)
        assertEquals(explicit, event.explicit)
      }

      fun hide(finishType: FinishType, isCurrentlyDisplaying: Boolean) = assert<InlineCompletionEventType.Hide> { event ->
        assertEquals(finishType, event.finishType)
        assertEquals(isCurrentlyDisplaying, event.isCurrentlyDisplaying)
      }

      fun variantComputed(variantIndex: Int) = assert<InlineCompletionEventType.VariantComputed> { event ->
        assertEquals(variantIndex, event.variantIndex)
      }

      fun computed(variantIndex: Int, element: String, i: Int) = assert<InlineCompletionEventType.Computed> { event ->
        assertEquals(variantIndex, event.variantIndex)
        assertEquals(element, event.element.text)
        assertEquals(i, event.i)
      }

      fun show(variantIndex: Int, element: String, i: Int) = assert<InlineCompletionEventType.Show> { event ->
        assertEquals(variantIndex, event.variantIndex)
        assertEquals(element, event.element.text)
        assertEquals(i, event.i)
      }

      fun change(variantIndex: Int, lengthDiff: Int) = assert<InlineCompletionEventType.Change> { event ->
        assertEquals(variantIndex, event.variantIndex)
        assertEquals(lengthDiff, event.lengthChange)
      }

      fun invalidated(variantIndex: Int) = assert<InlineCompletionEventType.Invalidated> { event ->
        assertEquals(variantIndex, event.variantIndex)
      }

      fun empty(variantIndex: Int) = assert<InlineCompletionEventType.Empty> { event ->
        assertEquals(variantIndex, event.variantIndex)
      }
    }
  }
}
