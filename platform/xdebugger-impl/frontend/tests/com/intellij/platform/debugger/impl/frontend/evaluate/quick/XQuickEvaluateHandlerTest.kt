// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.psi.PsiManager
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.resolvedPromise
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.lang.reflect.Proxy
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class XQuickEvaluateHandlerTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @TestDisposable
  lateinit var testDisposable: Disposable

  @Test
  @Timeout(30)
  fun `click without selection evaluates expression at pointer`(): Unit = timeoutRunBlocking {
    runQuickEvaluateTest(
      type = ValueHintType.MOUSE_CLICK_HINT,
      pointOffset = FALLBACK_RANGE.startOffset,
      selectionRange = null,
      expectedExpression = FALLBACK_EXPRESSION,
    )
  }

  @Test
  @Timeout(30)
  fun `ordinary hover without selection evaluates expression at pointer`(): Unit = timeoutRunBlocking {
    runQuickEvaluateTest(
      type = ValueHintType.MOUSE_OVER_HINT,
      pointOffset = FALLBACK_RANGE.startOffset,
      selectionRange = null,
      expectedExpression = FALLBACK_EXPRESSION,
    )
  }

  @Test
  @Timeout(30)
  fun `alt-hover without selection finds expression without immediate evaluation`(): Unit = timeoutRunBlocking {
    runQuickEvaluateTest(
      type = ValueHintType.MOUSE_ALT_OVER_HINT,
      pointOffset = FALLBACK_RANGE.startOffset,
      selectionRange = null,
      expectedExpression = null,
    )
  }

  @Test
  @Timeout(30)
  fun `click inside selection evaluates selected expression`(): Unit = timeoutRunBlocking {
    runQuickEvaluateTest(
      type = ValueHintType.MOUSE_CLICK_HINT,
      pointOffset = SELECTED_RANGE.startOffset,
      selectionRange = SELECTED_RANGE,
      expectedExpression = SELECTED_EXPRESSION,
    )
  }

  @Test
  @Timeout(30)
  fun `click outside selection evaluates expression at pointer`(): Unit = timeoutRunBlocking {
    runQuickEvaluateTest(
      type = ValueHintType.MOUSE_CLICK_HINT,
      pointOffset = FALLBACK_RANGE.startOffset,
      selectionRange = SELECTED_RANGE,
      expectedExpression = FALLBACK_EXPRESSION,
    )
  }

  @Test
  @Timeout(30)
  fun `click at selection end evaluates expression after selection`(): Unit = timeoutRunBlocking {
    runQuickEvaluateTest(
      type = ValueHintType.MOUSE_CLICK_HINT,
      pointOffset = SELECTED_RANGE.endOffset,
      selectionRange = SELECTED_RANGE,
      expectedExpression = FALLBACK_EXPRESSION,
    )
  }

  @Test
  @Timeout(30)
  fun `ordinary hover inside selection evaluates expression at pointer`(): Unit = timeoutRunBlocking {
    runQuickEvaluateTest(
      type = ValueHintType.MOUSE_OVER_HINT,
      pointOffset = SELECTED_RANGE.startOffset,
      selectionRange = SELECTED_RANGE,
      expectedExpression = FALLBACK_EXPRESSION,
    )
  }

  @Test
  @Timeout(30)
  fun `alt-hover inside selection creates manual selection hint without immediate evaluation`(): Unit = timeoutRunBlocking {
    runQuickEvaluateTest(
      type = ValueHintType.MOUSE_ALT_OVER_HINT,
      pointOffset = SELECTED_RANGE.startOffset,
      selectionRange = SELECTED_RANGE,
      expectedExpression = null,
    )
  }

  private suspend fun runQuickEvaluateTest(
    type: ValueHintType,
    pointOffset: Int,
    selectionRange: TextRange?,
    expectedExpression: String?,
  ) {
    val project = projectFixture.get()
    val evaluator = RecordingEvaluator(ExpressionInfo(FALLBACK_RANGE))
    installDebuggerSession(evaluator)
    var editor: Editor? = null
    var hint: AbstractValueHint? = null
    try {
      editor = createEditor(project)
      val handler = XQuickEvaluateHandler()
      val point = withContext(Dispatchers.EDT) {
        if (selectionRange != null) {
          editor.selectionModel.setSelection(selectionRange.startOffset, selectionRange.endOffset)
        }
        editor.offsetToXY(pointOffset)
      }
      val cancellableHint = withContext(Dispatchers.EDT) {
        handler.createValueHintAsync(project, editor, point, type)
      }
      hint = withTimeout(10.seconds) { cancellableHint.hintPromise().await() }
      assertNotNull(hint)

      withContext(Dispatchers.EDT) {
        hint.invokeHint()
      }

      if (expectedExpression != null) {
        assertEquals(expectedExpression, withTimeout(10.seconds) { evaluator.evaluatedExpression.await() })
      }
      else {
        assertFalse(evaluator.evaluatedExpression.isCompleted)
      }
    }
    finally {
      withContext(Dispatchers.EDT) {
        hint?.hideHint()
        editor?.let(EditorFactory.getInstance()::releaseEditor)
      }
    }
  }

  private fun installDebuggerSession(evaluator: XDebuggerEvaluator) {
    val session = proxy<XDebugSessionProxy> { methodName ->
      when (methodName) {
        "getCurrentEvaluator" -> evaluator
        "getEditorsProvider" -> TEST_EDITORS_PROVIDER
        "getValueMarkers", "getCurrentPosition" -> null
        else -> error("Unexpected XDebugSessionProxy call: $methodName")
      }
    }
    val manager = proxy<XDebugManagerProxy> { methodName ->
      when (methodName) {
        "isEnabled" -> true
        "getCurrentSessionProxy" -> session
        else -> error("Unexpected XDebugManagerProxy call: $methodName")
      }
    }
    ExtensionTestUtil.maskExtensions(X_DEBUG_MANAGER_PROXY_EP, listOf(manager), testDisposable)
  }

  private suspend fun createEditor(project: Project): Editor {
    return withContext(Dispatchers.EDT) {
      val file = LightVirtualFile("quickEvaluate.txt", PlainTextFileType.INSTANCE, DOCUMENT_TEXT)
      val document = FileDocumentManager.getInstance().getDocument(file)!!
      checkNotNull(PsiManager.getInstance(project).findFile(file))
      EditorFactory.getInstance().createEditor(document, project)
    }
  }

  private class RecordingEvaluator(private val expressionInfo: ExpressionInfo) : XDebuggerEvaluator() {
    val evaluatedExpression = CompletableDeferred<String>()

    override fun evaluate(expression: String, callback: XEvaluationCallback, expressionPosition: XSourcePosition?) {
      evaluatedExpression.complete(expression)
      callback.errorOccurred("Expected test evaluation stop")
    }

    override fun evaluate(expression: XExpression, callback: XEvaluationCallback, expressionPosition: XSourcePosition?) {
      evaluate(expression.expression, callback, expressionPosition)
    }

    override fun getExpressionInfoAtOffsetAsync(
      project: Project,
      document: Document,
      offset: Int,
      sideEffectsAllowed: Boolean,
    ): Promise<ExpressionInfo> {
      return resolvedPromise(expressionInfo)
    }
  }
}

private const val DOCUMENT_TEXT = "selected + value fallback"
private const val SELECTED_EXPRESSION = "selected + value "
private const val FALLBACK_EXPRESSION = "fallback"
private val SELECTED_RANGE = TextRange(0, SELECTED_EXPRESSION.length)
private val FALLBACK_RANGE = TextRange(DOCUMENT_TEXT.indexOf(FALLBACK_EXPRESSION), DOCUMENT_TEXT.length)

private val TEST_EDITORS_PROVIDER = object : XDebuggerEditorsProvider() {
  override fun getFileType() = PlainTextFileType.INSTANCE
}

private val X_DEBUG_MANAGER_PROXY_EP = ExtensionPointName.create<XDebugManagerProxy>("com.intellij.xdebugger.managerProxy")

private inline fun <reified T> proxy(crossinline methodResult: (String) -> Any?): T {
  return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, arguments ->
    when (method.name) {
      "equals" -> instance === arguments?.firstOrNull()
      "hashCode" -> System.identityHashCode(instance)
      "toString" -> "Test proxy for ${T::class.java.name}"
      else -> methodResult(method.name)
    }
  } as T
}
