// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.ide.rpc.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorApi
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorDto
import com.intellij.xdebugger.impl.rpc.XEvaluationResult
import com.intellij.xdebugger.impl.rpc.toRpc
import fleet.util.logging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val LOG = logger<FrontendXDebuggerEvaluator>()

internal fun createFrontendXDebuggerEvaluator(project: Project, evaluatorScope: CoroutineScope, evaluatorDto: XDebuggerEvaluatorDto): FrontendXDebuggerEvaluator {
  return if (evaluatorDto.canEvaluateInDocument) {
    FrontendXDebuggerDocumentOffsetEvaluator(project, evaluatorScope, evaluatorDto)
  }
  else {
    FrontendXDebuggerEvaluator(project, evaluatorScope, evaluatorDto)
  }
}

internal open class FrontendXDebuggerEvaluator(private val project: Project, private val evaluatorScope: CoroutineScope, private val evaluatorDto: XDebuggerEvaluatorDto) : XDebuggerEvaluator() {
  override fun evaluate(expression: String, callback: XEvaluationCallback, expressionPosition: XSourcePosition?) {
    evaluateByRpc(callback) {
      XDebuggerEvaluatorApi.getInstance().evaluate(evaluatorDto.id, expression, expressionPosition?.toRpc())
    }
  }

  override fun evaluate(expression: XExpression, callback: XEvaluationCallback, expressionPosition: XSourcePosition?) {
    evaluateByRpc(callback) {
      XDebuggerEvaluatorApi.getInstance().evaluateXExpression(evaluatorDto.id, expression.toRpc(), expressionPosition?.toRpc())
    }
  }

  protected fun evaluateByRpc(callback: XEvaluationCallback, evaluate: suspend () -> Deferred<XEvaluationResult>) {
    evaluatorScope.launch(Dispatchers.EDT) {
      try {
        val evaluation = evaluate().await()
        when (evaluation) {
          is XEvaluationResult.Evaluated -> callback.evaluated(FrontendXValue(project, evaluatorScope, evaluation.valueId, null))
          is XEvaluationResult.EvaluationError -> callback.errorOccurred(evaluation.errorMessage)
        }
      }
      catch (e: Exception) {
        callback.errorOccurred(e.message ?: XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"))
        LOG.error(e)
      }
    }
  }
}

private class FrontendXDebuggerDocumentOffsetEvaluator(
  project: Project,
  scope: CoroutineScope,
  private val evaluatorDto: XDebuggerEvaluatorDto,
) : FrontendXDebuggerEvaluator(project, scope, evaluatorDto), XDebuggerDocumentOffsetEvaluator {
  override fun evaluate(document: Document, offset: Int, hintType: ValueHintType, callback: XEvaluationCallback) {
    evaluateByRpc(callback) {
      XDebuggerEvaluatorApi.getInstance().evaluateInDocument(evaluatorDto.id, document.rpcId(), offset, hintType)
    }
  }
}