// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.EditorMouseFixture
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.junit.Assert.assertNull
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyEvent
import kotlin.reflect.KClass
import kotlin.time.Duration

@ApiStatus.Experimental
class InlineCompletionLifecycleTestDSL(val fixture: CodeInsightTestFixture) {
  @ICUtil
  suspend fun init(fileType: FileType, text: String = "") {
    coroutineToIndicator {
      fixture.configureByText(fileType, text)
    }
  }

  // Requests
  @ICRequest
  suspend fun createLookup(type: CompletionType = CompletionType.BASIC) {
    coroutineToIndicator {
      fixture.complete(type)
    }
  }

  @ICRequest
  suspend fun pickLookupElement(element: String) {
    withContext(Dispatchers.EDT) {
      assertNotNull(fixture.lookup)
      assertInstanceOf(LookupImpl::class.java, fixture.lookup)
      assertNotNull(fixture.lookupElementStrings)

      val lookupElement = fixture.lookupElements!!.find { it.lookupString == element }
      assertNotNull(lookupElement)
      fixture.lookup.currentItem = lookupElement
    }
  }

  @ICRequest
  suspend fun insertLookupElement() {
    withContext(Dispatchers.EDT) {
      val lookup = fixture.lookup as? LookupImpl
      assertNotNull(lookup?.currentItem)
      lookup!!.finishLookup('\n')
    }
  }

  @ICRequest
  suspend fun hideLookup() {
    withContext(Dispatchers.EDT) {
      val lookup = fixture.lookup as? LookupImpl
      assertNotNull(lookup)
      lookup!!.hideLookup(false)
    }
  }

  @ICRequest
  suspend fun typeChar(char: Char = '\n') {
    coroutineToIndicator {
      fixture.type(char)
    }
  }

  @ICUtil
  suspend fun navigateTo(position: Int) {
    withContext(Dispatchers.EDT) {
      fixture.editor.caretModel.moveToOffset(position)
      val pos = fixture.editor.caretModel.visualPosition

      EditorMouseFixture(fixture.editor as EditorImpl).pressAt(pos.line, pos.column)
    }
  }

  @ICUtil
  suspend fun navigateOnlyCaretTo(position: Int) {
    withContext(Dispatchers.EDT) {
      fixture.editor.caretModel.moveToOffset(position)
    }
  }

  @ICUtil
  suspend fun loseFocus(cause: FocusEvent.Cause = FocusEvent.Cause.UNKNOWN) {
    withContext(Dispatchers.EDT) {
      val ev = FocusEvent(fixture.editor.component, 0, false, null, cause)
      (fixture.editor as FocusListener).focusLost(ev)
    }
  }

  @ICRequest
  suspend fun callAction(actionId: String) {
    coroutineToIndicator {
      fixture.performEditorAction(actionId)
    }
  }

  @ICUtil
  suspend fun insert() {
    withContext(Dispatchers.EDT) {
      callAction(IdeActions.ACTION_INSERT_INLINE_COMPLETION)
      PsiDocumentManager.getInstance(fixture.project).commitDocument(fixture.editor.document)
    }
  }

  @ICUtil
  suspend fun insertWithTab() {
    withContext(Dispatchers.EDT) {
      val tabKeyEvent = KeyEvent(
        fixture.editor.component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
        0, KeyEvent.VK_TAB, KeyEvent.CHAR_UNDEFINED
      )
      IdeEventQueue.getInstance().dispatchEvent(tabKeyEvent)
      PsiDocumentManager.getInstance(fixture.project).commitDocument(fixture.editor.document)
    }
  }

  @ICUtil
  suspend fun escape() {
    callAction("EditorEscape")
  }

  @ICUtil
  suspend fun callInlineCompletion() {
    callAction(IdeActions.ACTION_CALL_INLINE_COMPLETION)
  }

  @ICUtil
  suspend fun backSpace() {
    callAction("EditorBackSpace")
  }

  @ICUtil
  suspend fun editorCopy() {
    callAction("EditorCopy")
  }

  @ICUtil
  suspend fun editorPaste() {
    callAction("EditorPaste")
  }

  @ICUtil
  suspend fun delay() {
    withContext(Dispatchers.EDT) {
      InlineCompletion.getHandlerOrNull(fixture.editor)?.awaitExecution()
    }
  }

  @ICUtil
  suspend fun delay(ms: Int) {
    kotlinx.coroutines.delay(ms.toLong())
  }

  @ICUtil
  inline fun caret(block: Caret.() -> Unit) {
    Caret().apply { block() }
  }

  @ICUtil
  suspend fun <T> withWriteAction(block: () -> T): T {
    return withContext(Dispatchers.EDT) {
      writeAction(block)
    }
  }

  @ICAssert
  suspend fun assertNoLookup() = readAction {
    assertNull(fixture.lookup)
  }

  @ICAssert
  suspend fun assertFileContent(context: String) {
    coroutineToIndicator {
      application.runReadAction {
        compareContents(context)
        compareCaretPosition(context)
      }
    }
  }

  @ICAssert
  suspend fun assertInlineRender(context: String) {
    withContext(Dispatchers.EDT) {
      val ctx = assertContextExists()
      assertEquals(context, ctx.textToInsert()) {
        "Expected and actual inline is shown and visible."
      }
    }
  }

  @ICAssert
  suspend fun assertInlineElements(builder: ExpectedInlineCompletionElementsBuilder.() -> Unit) {
    withContext(Dispatchers.EDT) {
      val expected = ExpectedInlineCompletionElementsBuilderImpl().apply(builder).build()
      assertInlineRender(expected.joinToString("") { it.text })
      val actual = assertContextExists().state.elements
      assertEquals(expected.size, actual.size) {
        "Unexpected number of inline elements. Expected: ${expected.map { it.text }}, found: ${actual.map { it.element.text }}."
      }
      (expected zip actual).forEach { (elem1, elem2) ->
        elem1.assertMatches(elem2)
      }
    }
  }

  @RequiresEdt
  @ICAssert
  suspend fun assertContextExists(): InlineCompletionContext = withContext(Dispatchers.EDT) {
    val contextOrNull = InlineCompletionContext.getOrNull(fixture.editor)
    assertNotNull(contextOrNull) { "There are no inline completion context." }
    contextOrNull!!
  }

  @ICAssert
  suspend fun assertInlineHidden() {
    withContext(Dispatchers.EDT) {
      val ctx = InlineCompletionContext.getOrNull(fixture.editor)
      Assertions.assertNull(ctx)
    }
  }

  //TODO: also check for fixture.file.text
  @RequiresReadLock
  @RequiresBlockingContext
  private fun compareContents(expectedLine: String) {
    assertEquals(expectedLine.removeCaret(), fixture.editor.document.text.removeCaret()) {
      "Expected and actual contents are different."
    }
  }

  @RequiresReadLock
  @RequiresBlockingContext
  private fun compareCaretPosition(expectedLine: String) {
    val actualLineWithCaret = StringBuilder(fixture.editor.document.text).insert(fixture.caretOffset, "<caret>").toString()
    assertEquals(expectedLine, actualLineWithCaret) {
      "Expected and actual caret positions are different."
    }
  }

  private fun String.removeCaret() = replace("<caret>", "")

  @DslMarker
  private annotation class ICRequest

  @DslMarker
  private annotation class ICUtil

  @DslMarker
  private annotation class ICAssert

  @ICUtil
  inner class Caret {

    @ICUtil
    suspend fun moveUp() {
      callAction("EditorUp")
    }

    @ICUtil
    suspend fun moveDown() {
      callAction("EditorDown")
    }

    @ICUtil
    suspend fun moveRight() {
      callAction("EditorRight")
    }

    @ICUtil
    suspend fun moveLeft() {
      callAction("EditorLeft")
    }
  }

  @ICUtil
  class InlineCompletionElementDescriptor(val text: String, val clazz: KClass<out InlineCompletionElement.Presentable>) {
    @ICUtil
    fun assertMatches(actual: InlineCompletionElement.Presentable) {
      assertInstanceOf(clazz.java, actual) {
        "Expected '${clazz.simpleName}' inline completion element, but '${actual::class.simpleName}' found."
      }
      assertEquals(text, actual.element.text) {
        "Expected inline completion element with '$text' content, but '${actual.element.text}' found."
      }
    }
  }

  @ICUtil
  interface ExpectedInlineCompletionElementsBuilder {

    fun add(descriptor: InlineCompletionElementDescriptor)
  }

  @ICUtil
  fun ExpectedInlineCompletionElementsBuilder.gray(text: String) {
    add(InlineCompletionElementDescriptor(text, InlineCompletionGrayTextElement.Presentable::class))
  }

  @ICUtil
  fun ExpectedInlineCompletionElementsBuilder.skip(text: String) {
    add(InlineCompletionElementDescriptor(text, InlineCompletionSkipTextElement.Presentable::class))
  }

  private class ExpectedInlineCompletionElementsBuilderImpl : ExpectedInlineCompletionElementsBuilder {

    private val elements = mutableListOf<InlineCompletionElementDescriptor>()

    override fun add(descriptor: InlineCompletionElementDescriptor) {
      elements += descriptor
    }

    @ICUtil
    fun build(): List<InlineCompletionElementDescriptor> = elements
  }
}

/**
 * This is **Experimental API**.
 *
 * If you use this DSL to write a test in JUnit3 or JUnit4 test classes, **please set `runInDispatchThread` to `false`**, otherwise, you'll
 * get a deadlock.
 *
 * Example: [com.intellij.codeInsight.inline.completion.SimpleInlineCompletionTest]
 */
@ApiStatus.Experimental
fun CodeInsightTestFixture.testInlineCompletion(
  timeout: Duration = DEFAULT_TEST_TIMEOUT,
  block: suspend InlineCompletionLifecycleTestDSL.() -> Unit
) {
  timeoutRunBlocking(timeout) {
    InlineCompletionLifecycleTestDSL(this@testInlineCompletion).apply {
      block()
    }
  }
}
