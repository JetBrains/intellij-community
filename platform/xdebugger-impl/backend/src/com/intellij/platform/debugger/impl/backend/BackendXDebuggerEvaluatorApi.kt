// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.DocumentId
import com.intellij.ide.rpc.document
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.evaluate.XEvaluationOrigin
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rpc.models.*
import com.intellij.xdebugger.impl.rpc.sourcePosition
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackWithOrigin
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.jetbrains.concurrency.asDeferred

internal class BackendXDebuggerEvaluatorApi : XDebuggerEvaluatorApi {
  override suspend fun evaluate(frameId: XStackFrameId, expression: String, position: XSourcePositionDto?, origin: XEvaluationOrigin): TimeoutSafeResult<XEvaluationResult> {
    return evaluate(frameId, origin) { evaluator, callback ->
      evaluator.evaluate(expression, callback, position?.sourcePosition())
    }
  }

  override suspend fun evaluateXExpression(frameId: XStackFrameId, expression: XExpressionDto, position: XSourcePositionDto?, origin: XEvaluationOrigin): TimeoutSafeResult<XEvaluationResult> {
    return evaluate(frameId, origin) { evaluator, callback ->
      evaluator.evaluate(expression.xExpression(), callback, position?.sourcePosition())
    }
  }

  override suspend fun evaluateInDocument(frameId: XStackFrameId, documentId: DocumentId, offset: Int, type: ValueHintType, origin: XEvaluationOrigin): TimeoutSafeResult<XEvaluationResult> {
    return evaluate(frameId, origin) { evaluator, callback ->
      val document = documentId.document()!!
      if (evaluator is XDebuggerDocumentOffsetEvaluator) {
        evaluator.evaluate(document, offset, type, callback)
      }
      else {
        callback.errorOccurred(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"))
      }
    }
  }

  override suspend fun expressionInfoAtOffset(frameId: XStackFrameId, documentId: DocumentId, offset: Int, sideEffectsAllowed: Boolean): ExpressionInfo? {
    val stackFrameModel = frameId.findValue() ?: return null
    val project = stackFrameModel.session.project
    val evaluator = stackFrameModel.stackFrame.evaluator ?: return null
    val document = documentId.document() ?: return null

    return readAction {
      evaluator.getExpressionInfoAtOffsetAsync(project, document, offset, sideEffectsAllowed)
    }.asDeferred().await()
  }

  private suspend fun evaluate(
    frameId: XStackFrameId,
    origin: XEvaluationOrigin,
    evaluateFun: suspend (XDebuggerEvaluator, XEvaluationCallback) -> Unit,
  ): TimeoutSafeResult<XEvaluationResult> {
    val stackFrameModel = frameId.findValue()
                          ?: return CompletableDeferred(XEvaluationResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.no.evaluator.id")))
    val evaluator = stackFrameModel.stackFrame.evaluator
                    ?: return CompletableDeferred(XEvaluationResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.no.evaluator.id")))
    val session = stackFrameModel.session
    val evaluationResult = CompletableDeferred<XEvaluationResult>()
    val evaluationCoroutineScope = session.coroutineScope

    val callback = object : XEvaluationCallback, XEvaluationCallbackWithOrigin {
      override fun getOrigin() = origin

      override fun evaluated(result: XValue) {
        evaluationCoroutineScope.launch {
          val xValueModel = newXValueModel(stackFrameModel, result, session)
          val xValueDto = xValueModel.toXValueDto()
          evaluationResult.complete(XEvaluationResult.Evaluated(xValueDto))
        }
      }

      override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
        evaluationResult.complete(XEvaluationResult.EvaluationError(errorMessage))
      }

      override fun invalidExpression(error: @NlsContexts.DialogMessage String) {
        evaluationResult.complete(XEvaluationResult.InvalidExpression(error))
      }
    }
    withContext(Dispatchers.EDT) {
      evaluateFun(evaluator, callback)
    }

    return evaluationResult
  }
}

internal fun BackendXValueGroupModel.toXValueGroupDto(): XValueGroupDto {
  return XValueGroupDto(
    id,
    xValueGroup.name,
    xValueGroup.icon?.rpcId(),
    xValueGroup.isAutoExpand,
    xValueGroup.isRestoreExpansion,
    xValueGroup.separator,
    xValueGroup.comment
  )
}

internal fun newXValueModel(
  stackFrameModel: XStackFrameModel,
  xValue: XValue,
  session: XDebugSessionImpl,
): BackendXValueModel {
  val xValueModel = BackendXValueModelsManager.getInstance(session.project).createXValueModel(stackFrameModel.coroutineScope, session, xValue)
  return xValueModel.apply {
    setInitialMarker()
  }
}

internal fun newChildXValueModel(
  xValue: XValue,
  parentCoroutineScope: CoroutineScope,
  session: XDebugSessionImpl,
): BackendXValueModel {
  val xValueModel = BackendXValueModelsManager.getInstance(session.project).createXValueModel(parentCoroutineScope, session, xValue)
  return xValueModel.apply {
    setInitialMarker()
  }
}

private fun BackendXValueModel.setInitialMarker() {
  cs.launch {
    xValue.isReady.await()
    val markers = session.valueMarkers
    val marker = markers?.getMarkup(xValue) ?: return@launch
    setMarker(marker)
  }
}
