// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackWithOrigin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Evaluates the expression by given [expression] in the [expressionPosition] or in current suspend context if [expressionPosition] is null.
 *
 * If the evaluation is called from a specific place, provide it trough [origin].
 *
 * @throws XEvaluationException if evaluation fails
 * @throws XInvalidExpressionException if the expression is invalid in the current context
 */
@ApiStatus.Internal
suspend fun XDebuggerEvaluator.evaluateSuspend(
  expression: String,
  expressionPosition: XSourcePosition?,
  origin: XEvaluationOrigin = XEvaluationOrigin.UNSPECIFIED,
): XValue {
  return evaluateImpl(origin) { callback ->
    evaluate(expression, callback, expressionPosition)
  }
}

/**
 * Evaluates the expression by given [XExpression] in the [expressionPosition] or in current suspend context if [expressionPosition] is null.
 *
 * If the evaluation is called from a specific place, provide it through [origin].
 *
 * @throws XEvaluationException if evaluation fails
 * @throws XInvalidExpressionException if the expression is invalid in the current context
 */
@ApiStatus.Internal
suspend fun XDebuggerEvaluator.evaluateSuspend(
  expression: XExpression,
  expressionPosition: XSourcePosition?,
  origin: XEvaluationOrigin = XEvaluationOrigin.UNSPECIFIED,
): XValue {
  return evaluateImpl(origin) { callback ->
    evaluate(expression, callback, expressionPosition)
  }
}

/**
 * Evaluates expression at the given [document] in its [offset] called by a specific [hintType].
 *
 * If the evaluation is called from a specific place, provide it through [origin].
 *
 * @throws XEvaluationException if evaluation fails
 * @throws XInvalidExpressionException if the expression is invalid in the current context
 */
@ApiStatus.Internal
suspend fun XDebuggerDocumentOffsetEvaluator.evaluateSuspend(
  document: Document,
  offset: Int,
  hintType: ValueHintType,
  origin: XEvaluationOrigin = XEvaluationOrigin.UNSPECIFIED,
): XValue {
  return evaluateImpl(origin) { callback ->
    evaluate(document, offset, hintType, callback)
  }
}

/**
 * Exception thrown when expression evaluation fails.
 */
@ApiStatus.Internal
open class XEvaluationException(val errorMessage: @NlsContexts.DialogMessage String) : Exception(errorMessage)

/**
 * Exception thrown when the expression is invalid in the current context.
 */
@ApiStatus.Internal
class XInvalidExpressionException(val error: @NlsContexts.DialogMessage String) : XEvaluationException(error)

private suspend fun evaluateImpl(
  origin: XEvaluationOrigin,
  evaluate: (XDebuggerEvaluator.XEvaluationCallback) -> Unit,
): XValue {
  val result = CompletableDeferred<XValue>()
  val callback = object : XDebuggerEvaluator.XEvaluationCallback, XEvaluationCallbackWithOrigin {
    override fun evaluated(value: XValue) {
      result.complete(value)
    }

    override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
      result.completeExceptionally(XEvaluationException(errorMessage))
    }

    override fun invalidExpression(error: @NlsContexts.DialogMessage String) {
      result.completeExceptionally(XInvalidExpressionException(error))
    }

    override fun getOrigin(): XEvaluationOrigin = origin
  }

  withContext(Dispatchers.EDT) {
    evaluate(callback)
  }
  return result.await()
}
