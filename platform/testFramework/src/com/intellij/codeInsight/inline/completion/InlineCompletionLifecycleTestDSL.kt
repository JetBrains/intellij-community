// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.EditorMouseFixture
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.annotations.ApiStatus
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
      assertThat(fixture.lookup).isNotNull()
      assertThat(fixture.lookup).isInstanceOf(LookupImpl::class.java)
      assertThat(fixture.lookupElementStrings).isNotNull()

      val lookupElement = fixture.lookupElements!!.find { it.lookupString == element }
      assertThat(lookupElement).isNotNull()
      writeIntentReadAction {
        fixture.lookup.currentItem = lookupElement
      }
    }
  }

  @ICRequest
  suspend fun insertLookupElement() {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val lookup = fixture.lookup as? LookupImpl
        assertThat(lookup?.currentItem).isNotNull()
        lookup!!.finishLookup('\n')
      }
    }
  }

  @ICRequest
  suspend fun hideLookup() {
    withContext(Dispatchers.EDT) {
      val lookup = fixture.lookup as? LookupImpl
      assertThat(lookup).isNotNull()
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
      writeIntentReadAction {
        fixture.editor.caretModel.moveToOffset(position)
        val pos = fixture.editor.caretModel.visualPosition

        EditorMouseFixture(fixture.editor as EditorImpl).pressAt(pos.line, pos.column)
      }
    }
  }

  @ICUtil
  suspend fun navigateOnlyCaretTo(position: Int) {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        fixture.editor.caretModel.moveToOffset(position)
      }
    }
  }

  @ICUtil
  suspend fun loseFocus(cause: FocusEvent.Cause = FocusEvent.Cause.UNKNOWN) {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val ev = FocusEvent(fixture.editor.component, 0, false, null, cause)
        (fixture.editor as FocusListener).focusLost(ev)
      }
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
      writeIntentReadAction {
        PsiDocumentManager.getInstance(fixture.project).commitDocument(fixture.editor.document)
      }
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
      writeIntentReadAction {
        PsiDocumentManager.getInstance(fixture.project).commitDocument(fixture.editor.document)
      }
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
  @ApiStatus.Experimental
  suspend fun nextVariant() {
    withContext(Dispatchers.EDT) {
      coroutineToIndicator {
        WriteIntentReadAction.run {
          InlineCompletionSession.getOrNull(fixture.editor)?.useNextVariant()
        }
      }
    }
  }

  @ICUtil
  @ApiStatus.Experimental
  suspend fun prevVariant() {
    withContext(Dispatchers.EDT) {
      coroutineToIndicator {
        WriteIntentReadAction.run {
          InlineCompletionSession.getOrNull(fixture.editor)?.usePrevVariant()
        }
      }
    }
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
    delay(ms.toLong())
  }

  @ICUtil
  suspend fun delay(ms: Long) {
    kotlinx.coroutines.delay(ms)
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
    assertThat(fixture.lookup).isNull()
  }

  @ICAssert
  suspend fun assertFileContent(context: String) {
    coroutineToIndicator {
      ApplicationManager.getApplication().runReadAction {
        compareContents(context)
        compareCaretPosition(context)
      }
    }
  }

  @ICAssert
  suspend fun assertInlineRender(context: String) {
    withContext(Dispatchers.EDT) {
      val ctx = assertContextExists()
      assertThat(ctx.textToInsert()).describedAs { "Expected and actual inline is shown and visible." }.isEqualTo(context)
    }
  }

  @ICAssert
  suspend fun assertInlineElements(builder: ExpectedInlineCompletionElementsBuilder.() -> Unit) {
    withContext(Dispatchers.EDT) {
      val expected = ExpectedInlineCompletionElementsBuilderImpl().apply(builder).build()
      assertInlineRender(expected.joinToString("") { it.text })
      val actual = assertContextExists().state.elements
      assertThat(actual.size).describedAs {
        "Unexpected number of inline elements. Expected: ${expected.map { it.text }}, found: ${actual.map { it.element.text }}."
      }.isEqualTo(expected.size)
      (expected zip actual).forEach { (elem1, elem2) ->
        elem1.assertMatches(elem2)
      }
    }
  }

  @RequiresEdt
  @ICAssert
  suspend fun assertContextExists(): InlineCompletionContext = withContext(Dispatchers.EDT) {
    val contextOrNull = InlineCompletionContext.getOrNull(fixture.editor)
    assertThat(contextOrNull).describedAs { "There are no inline completion context." }.isNotNull()
    contextOrNull!!
  }

  @ICAssert
  suspend fun assertInlineHidden() {
    withContext(Dispatchers.EDT) {
      val ctx = InlineCompletionContext.getOrNull(fixture.editor)
      assertThat(ctx).isNull()
    }
  }

  //TODO: also check for fixture.file.text
  @RequiresReadLock
  @RequiresBlockingContext
  private fun compareContents(expectedLine: String) {
    assertThat(fixture.editor.document.text.removeCaret()).describedAs {
      "Expected and actual contents are different."
    }.isEqualTo(expectedLine.removeCaret())
  }

  @RequiresReadLock
  @RequiresBlockingContext
  private fun compareCaretPosition(expectedLine: String) {
    val actualLineWithCaret = StringBuilder(fixture.editor.document.text).insert(fixture.caretOffset, "<caret>").toString()
    assertThat(actualLineWithCaret).describedAs {
      "Expected and actual caret positions are different."
    }.isEqualTo(expectedLine)
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
  abstract class InlineCompletionElementPredicate(val text: String) {
    abstract fun assertMatches(actual: InlineCompletionElement.Presentable)
  }

  @ICUtil
  class InlineCompletionElementDescriptor(
    text: String,
    val clazz: KClass<out InlineCompletionElement.Presentable>
  ) : InlineCompletionElementPredicate(text) {
    @ICUtil
    override fun assertMatches(actual: InlineCompletionElement.Presentable) {
      assertThat(actual).describedAs {
        "Expected '${clazz.simpleName}' inline completion element, but '${actual::class.simpleName}' found."
      }.isInstanceOf(clazz.java)
      assertThat(actual.element.text).describedAs {
        "Expected inline completion element with '$text' content, but '${actual.element.text}' found."
      }.isEqualTo(text)
    }
  }

  @ICUtil
  sealed interface ExpectedInlineCompletionElementsBuilder {

    fun add(descriptor: InlineCompletionElementPredicate)
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

    private val elements = mutableListOf<InlineCompletionElementPredicate>()

    override fun add(descriptor: InlineCompletionElementPredicate) {
      elements += descriptor
    }

    @ICUtil
    fun build(): List<InlineCompletionElementPredicate> = elements
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
