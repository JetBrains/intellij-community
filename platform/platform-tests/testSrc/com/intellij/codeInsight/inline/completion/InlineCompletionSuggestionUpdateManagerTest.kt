// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager.UpdateResult
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.codeInsight.inline.completion.suggestion.invoke
import com.intellij.codeInsight.lookup.impl.LookupActionHandler
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class InlineCompletionSuggestionUpdateManagerTest : InlineCompletionTestCase() {

  @Test
  fun `test custom over typing`() = myFixture.testInlineCompletion {
    val key = Key.create<Int>("inline.completion.test.number")

    // Doubles inline completion elements after each typing
    // Also, records number of elements to [key]
    val updateManager = object : InlineCompletionSuggestionUpdateManager.Adapter {
      override fun onDocumentChange(
        event: InlineCompletionEvent.DocumentChange,
        variant: InlineCompletionVariant.Snapshot
      ): UpdateResult {
        val newElements = variant.elements + variant.elements
        variant.data.putUserData(key, newElements.size)
        return UpdateResult.Changed(variant.copy(elements = newElements))
      }
    }
    val provider = object : InlineCompletionProviderBase("1") {
      override val suggestionUpdateManager = updateManager
    }
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)

    init(PlainTextFileType.INSTANCE)
    callInlineCompletion()
    delay()
    assertInlineRender("1")
    assertEquals(null, assertContextExists().getUserData(key))

    typeChar('x')
    assertInlineRender("11")
    assertEquals(2, assertContextExists().getUserData(key))

    typeChar('x')
    assertInlineRender("1111")
    assertEquals(4, assertContextExists().getUserData(key))

    insert()
    assertFileContent("xx1111<caret>")
  }

  @Test
  fun `test update on lookup events`() = myFixture.testInlineCompletion {
    // Changes inline completion element to 'provided prefix + item string'
    val updateManager = object : InlineCompletionSuggestionUpdateManager.Adapter {
      override fun onLookupEvent(
        event: InlineCompletionEvent.InlineLookupEvent,
        variant: InlineCompletionVariant.Snapshot
      ): UpdateResult {
        val item = event.event.item
        return if (event !is InlineCompletionEvent.LookupChange || item == null) {
          UpdateResult.Same
        }
        else {
          UpdateResult.Changed(variant.copy(listOf(variant.elements.first(), InlineCompletionGrayTextElement(item.lookupString))))
        }
      }
    }
    val provider = object : InlineCompletionProviderBase("inline is ") {
      override val suggestionUpdateManager = updateManager
    }
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)

    init(PlainTextFileType.INSTANCE)
    callInlineCompletion()
    delay()

    assertInlineRender("inline is ")
    val variants = arrayOf("super", "the best", "one thing I love")
    fillLookup(*variants)
    createLookup()

    for (variant in variants) {
      pickLookupElement(variant)
      assertInlineRender("inline is $variant")
    }

    insert()
    assertFileContent("inline is one thing I love<caret>")
  }

  @Test
  fun `test lookup navigation updates variants without read access`() = myFixture.testInlineCompletion {
    val updates = mutableListOf<String>()
    prepareLookup(updates)
    val lookup = withContext(Dispatchers.EDT) { myFixture.lookup as LookupImpl }

    withContext(Dispatchers.UI) {
      assertFalse(application.isReadAccessAllowed)
      LookupActionHandler.DownHandler(null).doExecute(lookup.topLevelEditor, null, DataContext.EMPTY_CONTEXT)
      LookupActionHandler.UpHandler(null).doExecute(lookup.topLevelEditor, null, DataContext.EMPTY_CONTEXT)
      LookupActionHandler.DownHandler(null).doExecute(lookup.topLevelEditor, null, DataContext.EMPTY_CONTEXT)
    }
    awaitLookupUpdate()

    assertEquals(listOf("second"), updates)
    assertInlineRender("second")
  }

  @Test
  fun `test closing a lookup discards a pending variant update`() = myFixture.testInlineCompletion {
    val updates = mutableListOf<String>()
    prepareLookup(updates)
    val lookup = withContext(Dispatchers.EDT) { myFixture.lookup as LookupImpl }

    withContext(Dispatchers.UI) {
      LookupActionHandler.DownHandler(null).doExecute(lookup.topLevelEditor, null, DataContext.EMPTY_CONTEXT)
      lookup.hideLookup(false)
    }
    awaitLookupUpdate()

    assertTrue(updates.isEmpty())
    assertInlineRender("first")
  }

  @Test
  fun `test a pending lookup update does not affect a new session`() = myFixture.testInlineCompletion {
    val updates = mutableListOf<String>()
    prepareLookup(updates)
    val lookup = withContext(Dispatchers.EDT) { myFixture.lookup as LookupImpl }

    withContext(Dispatchers.UI) {
      application.invokeLater {
        val handler = InlineCompletion.getHandlerOrNull(myFixture.editor)!!
        val previousSession = InlineCompletionSession.getOrNull(myFixture.editor)!!
        handler.hide(previousSession.context)
        handler.invokeEvent(InlineCompletionEvent.ManualCall(myFixture.editor, previousSession.provider.id, UserDataHolderBase()))
        assertNotSame(previousSession, InlineCompletionSession.getOrNull(myFixture.editor))
      }
      LookupActionHandler.DownHandler(null).doExecute(lookup.topLevelEditor, null, DataContext.EMPTY_CONTEXT)
    }
    awaitLookupUpdate()
    delay()

    assertTrue(updates.isEmpty())
    assertInlineRender("initial")
  }

  private suspend fun InlineCompletionLifecycleTestDSL.prepareLookup(updates: MutableList<String>) {
    val provider = object : InlineCompletionProviderBase("initial") {
      override fun isEnabled(event: InlineCompletionEvent): Boolean {
        return super.isEnabled(event) || event is InlineCompletionEvent.ManualCall
      }

      override val suggestionUpdateManager = object : InlineCompletionSuggestionUpdateManager.Adapter {
        override fun updateWhileNoVariants(event: InlineCompletionEvent): Boolean {
          if (event is InlineCompletionEvent.LookupChange) {
            event.event.item?.lookupString?.let(updates::add)
          }
          return true
        }

        override fun onLookupEvent(
          event: InlineCompletionEvent.InlineLookupEvent,
          variant: InlineCompletionVariant.Snapshot,
        ): UpdateResult {
          if (event !is InlineCompletionEvent.LookupChange) return UpdateResult.Same
          val text = event.event.item?.lookupString ?: return UpdateResult.Same
          updates.add(text)
          return UpdateResult.Changed(variant.copy(listOf(InlineCompletionGrayTextElement(text))))
        }
      }
    }
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)
    init(PlainTextFileType.INSTANCE)
    callInlineCompletion()
    delay()
    fillLookup("first", "second")
    createLookup()
    pickLookupElement("first")
    awaitLookupUpdate()
    updates.clear()
  }

  private suspend fun awaitLookupUpdate() {
    val completed = CompletableDeferred<Unit>()
    withContext(Dispatchers.UI) { application.invokeLater { completed.complete(Unit) } }
    completed.await()
  }

  private abstract class InlineCompletionProviderBase(private vararg val variants: String) : InlineCompletionProvider {
    override val id: InlineCompletionProviderID = InlineCompletionProviderID("TEST")

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
      return InlineCompletionSuggestion {
        variants.forEach {
          variant { _ ->
            emit(InlineCompletionGrayTextElement(it))
          }
        }
      }
    }

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
      return event is InlineCompletionEvent.DirectCall
    }
  }
}
