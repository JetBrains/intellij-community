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
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.EditorMouseFixture
import com.intellij.util.application
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
  fun init(fileType: FileType, text: String = "") {
    fixture.configureByText(fileType, text)
  }

  // Requests
  @ICRequest
  fun createLookup(type: CompletionType = CompletionType.BASIC) {
    fixture.complete(type)
  }

  @ICRequest
  fun pickLookupElement(element: String) = application.invokeAndWait {
    assertNotNull(fixture.lookup)
    assertInstanceOf(LookupImpl::class.java, fixture.lookup)
    assertNotNull(fixture.lookupElementStrings)

    val lookupElement = fixture.lookupElements!!.find { it.lookupString == element }
    assertNotNull(lookupElement)
    fixture.lookup.currentItem = lookupElement
  }

  @ICRequest
  fun insertLookupElement() = application.invokeAndWait {
    val lookup = fixture.lookup as? LookupImpl
    assertNotNull(lookup?.currentItem)
    lookup!!.finishLookup('\n')
  }

  @ICRequest
  fun hideLookup() = application.invokeAndWait {
    val lookup = fixture.lookup as? LookupImpl
    assertNotNull(lookup)
    lookup!!.hideLookup(false)
  }

  @ICRequest
  fun typeChar(char: Char = '\n') {
    fixture.type(char)
  }

  @ICUtil
  fun navigateTo(position: Int) = application.invokeAndWait {
    fixture.editor.caretModel.moveToOffset(position)
    val pos = fixture.editor.caretModel.visualPosition

    EditorMouseFixture(fixture.editor as EditorImpl).pressAt(pos.line, pos.column)
  }

  @ICUtil
  fun navigateOnlyCaretTo(position: Int) = application.invokeAndWait {
    fixture.editor.caretModel.moveToOffset(position)
  }

  @ICUtil
  fun loseFocus(cause: FocusEvent.Cause = FocusEvent.Cause.UNKNOWN) = application.invokeAndWait {
    val ev = FocusEvent(fixture.editor.component, 0, false, null, cause)
    (fixture.editor as FocusListener).focusLost(ev)
  }

  @ICRequest
  fun callAction(actionId: String) {
    fixture.performEditorAction(actionId)
  }

  @ICUtil
  fun insert() = application.invokeAndWait {
    callAction(IdeActions.ACTION_INSERT_INLINE_COMPLETION)
    PsiDocumentManager.getInstance(fixture.project).commitDocument(fixture.editor.document)
  }

  @ICUtil
  fun insertWithTab() = application.invokeAndWait {
    val tabKeyEvent = KeyEvent(
      fixture.editor.component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
      0, KeyEvent.VK_TAB, KeyEvent.CHAR_UNDEFINED
    )
    IdeEventQueue.getInstance().dispatchEvent(tabKeyEvent)
    PsiDocumentManager.getInstance(fixture.project).commitDocument(fixture.editor.document)
  }

  @ICUtil
  fun escape() = callAction("EditorEscape")

  @ICUtil
  fun callInlineCompletion() = callAction(IdeActions.ACTION_CALL_INLINE_COMPLETION)

  @ICUtil
  fun backSpace() = callAction("EditorBackSpace")

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
    Caret().apply(block)
  }

  @ICUtil
  fun editorCopy() {
    callAction("EditorCopy")
  }

  @ICUtil
  fun editorPaste() {
    callAction("EditorPaste")
  }

  @ICAssert
  fun assertNoLookup() = application.runReadAction {
    assertNull(fixture.lookup)
  }

  @ICAssert
  fun assertFileContent(context: String) = application.runReadAction {
    compareContents(context)
    compareCaretPosition(context)
  }

  @ICAssert
  fun assertInlineRender(context: String) = application.invokeAndWait {
    val ctx = assertContextExists()
    assertEquals(context, ctx.textToInsert()) {
      "Expected and actual inline is shown and visible."
    }
  }

  @ICAssert
  fun assertInlineElements(builder: ExpectedInlineCompletionElementsBuilder.() -> Unit) = application.invokeAndWait {
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

  @RequiresEdt
  @ICAssert
  fun assertContextExists(): InlineCompletionContext {
    lateinit var context: InlineCompletionContext
    application.invokeAndWait {
      val contextOrNull = InlineCompletionContext.getOrNull(fixture.editor)
      assertNotNull(contextOrNull) { "There are no inline completion context." }
      context = contextOrNull!!
    }
    return context
  }

  @ICAssert
  fun assertInlineHidden() = application.invokeAndWait {
    val ctx = InlineCompletionContext.getOrNull(fixture.editor)
    Assertions.assertNull(ctx)
  }

  //TODO: also check for fixture.file.text
  @RequiresReadLock
  private fun compareContents(expectedLine: String) {
    assertEquals(expectedLine.removeCaret(), fixture.editor.document.text.removeCaret()) {
      "Expected and actual contents are different."
    }
  }

  @RequiresReadLock
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
    fun moveUp() = callAction("EditorUp")

    @ICUtil
    fun moveDown() = callAction("EditorDown")

    @ICUtil
    fun moveRight() = callAction("EditorRight")

    @ICUtil
    fun moveLeft() = callAction("EditorLeft")
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
