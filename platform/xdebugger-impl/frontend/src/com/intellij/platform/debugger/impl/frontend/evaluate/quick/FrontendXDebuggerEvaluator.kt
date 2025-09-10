// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.ide.rpc.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackWithOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import kotlin.coroutines.cancellation.CancellationException

private val LOG = logger<FrontendXDebuggerEvaluator>()

internal fun createFrontendXDebuggerEvaluator(
  project: Project,
  evaluatorScope: CoroutineScope,
  evaluatorDto: XDebuggerEvaluatorDto,
  frameId: XStackFrameId,
): FrontendXDebuggerEvaluator {
  return if (evaluatorDto.canEvaluateInDocument) {
    FrontendXDebuggerDocumentOffsetEvaluator(project, evaluatorScope, frameId)
  }
  else {
    FrontendXDebuggerEvaluator(project, evaluatorScope, frameId)
  }
}

internal open class FrontendXDebuggerEvaluator(
  private val project: Project,
  private val evaluatorScope: CoroutineScope,
  val frameId: XStackFrameId,
) : XDebuggerEvaluator() {
  override fun evaluate(expression: String, callback: XEvaluationCallback, expressionPosition: XSourcePosition?) {
    evaluateByRpc(callback) {
      val origin = XEvaluationCallbackWithOrigin.getOrigin(callback)
      XDebuggerEvaluatorApi.getInstance().evaluate(frameId, expression, expressionPosition?.toRpc(), origin)
    }
  }

  override fun evaluate(expression: XExpression, callback: XEvaluationCallback, expressionPosition: XSourcePosition?) {
    evaluateByRpc(callback) {
      val origin = XEvaluationCallbackWithOrigin.getOrigin(callback)
      XDebuggerEvaluatorApi.getInstance().evaluateXExpression(frameId, expression.toRpc(), expressionPosition?.toRpc(), origin)
    }
  }

  protected fun evaluateByRpc(callback: XEvaluationCallback, evaluate: suspend () -> TimeoutSafeResult<XEvaluationResult>) {
    evaluatorScope.launch(Dispatchers.EDT) {
      try {
        when (val evaluation = evaluate().await()) {
          is XEvaluationResult.Evaluated -> callback.evaluated(FrontendXValue.create(project, evaluatorScope, evaluation.valueId, false))
          is XEvaluationResult.EvaluationError -> callback.errorOccurred(evaluation.errorMessage)
          is XEvaluationResult.InvalidExpression -> callback.invalidExpression(evaluation.error)
        }
      }
      catch (e: Exception) {
        callback.errorOccurred(e.message ?: XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"))
        if (e is CancellationException || e is ControlFlowException) {
          throw e
        }
        LOG.error(e)
      }
    }
  }

  override fun getExpressionInfoAtOffsetAsync(project: Project, document: Document, offset: Int, sideEffectsAllowed: Boolean): Promise<ExpressionInfo?> {
    return evaluatorScope.future {
      XDebuggerEvaluatorApi.getInstance().expressionInfoAtOffset(frameId, document.rpcId(), offset, sideEffectsAllowed)
    }.asPromise()
  }
}

private class FrontendXDebuggerDocumentOffsetEvaluator(
  project: Project,
  scope: CoroutineScope,
  frameId: XStackFrameId,
) : FrontendXDebuggerEvaluator(project, scope, frameId), XDebuggerDocumentOffsetEvaluator {
  override fun evaluate(document: Document, offset: Int, hintType: ValueHintType, callback: XEvaluationCallback) {
    evaluateByRpc(callback) {
      val origin = XEvaluationCallbackWithOrigin.getOrigin(callback)
      XDebuggerEvaluatorApi.getInstance().evaluateInDocument(frameId, document.rpcId(), offset, hintType, origin)
    }
  }
}