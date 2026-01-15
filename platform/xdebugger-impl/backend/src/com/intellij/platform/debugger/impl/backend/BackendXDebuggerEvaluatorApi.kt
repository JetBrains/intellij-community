// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.DocumentId
import com.intellij.ide.rpc.document
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.readAction
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.evaluate.XEvaluationException
import com.intellij.xdebugger.impl.evaluate.XEvaluationOrigin
import com.intellij.xdebugger.impl.evaluate.XInvalidExpressionException
import com.intellij.xdebugger.impl.evaluate.evaluateSuspend
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rpc.models.*
import com.intellij.xdebugger.impl.rpc.sourcePosition
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.jetbrains.concurrency.asDeferred

internal class BackendXDebuggerEvaluatorApi : XDebuggerEvaluatorApi {
  override suspend fun evaluate(frameId: XStackFrameId, expression: String, position: XSourcePositionDto?, origin: XEvaluationOrigin): TimeoutSafeResult<XEvaluationResult> {
    return evaluate(frameId) { evaluator ->
      evaluator.evaluateSuspend(expression, position?.sourcePosition(), origin)
    }
  }

  override suspend fun evaluateXExpression(frameId: XStackFrameId, expression: XExpressionDto, position: XSourcePositionDto?, origin: XEvaluationOrigin): TimeoutSafeResult<XEvaluationResult> {
    return evaluate(frameId) { evaluator ->
      evaluator.evaluateSuspend(expression.xExpression(), position?.sourcePosition(), origin)
    }
  }

  override suspend fun evaluateInDocument(frameId: XStackFrameId, documentId: DocumentId, offset: Int, type: ValueHintType, origin: XEvaluationOrigin): TimeoutSafeResult<XEvaluationResult> {
    return evaluate(frameId) { evaluator ->
      val document = documentId.document()!!
      if (evaluator is XDebuggerDocumentOffsetEvaluator) {
        evaluator.evaluateSuspend(document, offset, type, origin)
      }
      else {
        throw XEvaluationException(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"))
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

  private fun evaluate(
    frameId: XStackFrameId,
    evaluateFun: suspend (XDebuggerEvaluator) -> XValue,
  ): TimeoutSafeResult<XEvaluationResult> {
    val stackFrameModel = frameId.findValue()
                          ?: return CompletableDeferred(XEvaluationResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.no.evaluator.id")))
    val evaluator = stackFrameModel.stackFrame.evaluator
                    ?: return CompletableDeferred(XEvaluationResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.no.evaluator.id")))
    val session = stackFrameModel.session
    val evaluationCoroutineScope = session.coroutineScope

    return evaluationCoroutineScope.async {
      try {
        val xValue = evaluateFun(evaluator)
        val xValueModel = newXValueModel(stackFrameModel, xValue, session)
        val xValueDto = xValueModel.toXValueDtoWithPresentation()
        XEvaluationResult.Evaluated(xValueDto)
      }
      catch (e: XInvalidExpressionException) {
        XEvaluationResult.InvalidExpression(e.error)
      }
      catch (e: XEvaluationException) {
        XEvaluationResult.EvaluationError(e.errorMessage)
      }
    }
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
