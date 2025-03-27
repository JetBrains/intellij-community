// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.DocumentId
import com.intellij.ide.rpc.document
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModelsManager
import com.intellij.xdebugger.impl.rhizome.XValueMarkerDto
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.rpc.models.findValue
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.toRpc
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import org.jetbrains.concurrency.asDeferred

internal class BackendXDebuggerEvaluatorApi : XDebuggerEvaluatorApi {
  override suspend fun evaluate(frameId: XStackFrameId, expression: String, position: XSourcePositionDto?): Deferred<XEvaluationResult> {
    return evaluate(frameId) { project, evaluator, callback ->
      evaluator.evaluate(expression, callback, position?.sourcePosition())
    }
  }

  override suspend fun evaluateXExpression(frameId: XStackFrameId, expression: XExpressionDto, position: XSourcePositionDto?): Deferred<XEvaluationResult> {
    return evaluate(frameId) { project, evaluator, callback ->
      evaluator.evaluate(expression.xExpression(), callback, position?.sourcePosition())
    }
  }

  override suspend fun evaluateInDocument(frameId: XStackFrameId, documentId: DocumentId, offset: Int, type: ValueHintType): Deferred<XEvaluationResult> {
    return evaluate(frameId) { project, evaluator, callback ->
      val document = documentId.document()!!
      if (evaluator is XDebuggerDocumentOffsetEvaluator) {
        evaluator.evaluate(document, offset, type, callback)
      }
      else {
        callback.errorOccurred(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"))
      }
    }
  }

  private suspend fun evaluate(
    frameId: XStackFrameId,
    evaluateFun: suspend (Project, XDebuggerEvaluator, XEvaluationCallback) -> Unit,
  ): Deferred<XEvaluationResult> {
    val stackFrameModel = frameId.findValue()
                          ?: return CompletableDeferred(XEvaluationResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.no.evaluator.id")))
    val evaluator = stackFrameModel.stackFrame.evaluator
                    ?: return CompletableDeferred(XEvaluationResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.no.evaluator.id")))
    val session = stackFrameModel.session
    val evaluationResult = CompletableDeferred<XValue>()

    withContext(Dispatchers.EDT) {
      val callback = object : XEvaluationCallback {
        override fun evaluated(result: XValue) {
          evaluationResult.complete(result)
        }

        override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
          evaluationResult.completeExceptionally(EvaluationException(errorMessage))
        }
      }
      evaluateFun(session.project, evaluator, callback)
    }
    val evaluationCoroutineScope = EvaluationCoroutineScopeProvider.getInstance().cs

    return evaluationCoroutineScope.async(Dispatchers.EDT) {
      val xValue = try {
        evaluationResult.await()
      }
      catch (e: EvaluationException) {
        return@async XEvaluationResult.EvaluationError(e.errorMessage)
      }
      val xValueModel = newXValueModel(xValue, session)
      val xValueDto = xValueModel.toXValueDto()
      XEvaluationResult.Evaluated(xValueDto)
    }
  }

  private class EvaluationException(val errorMessage: @NlsContexts.DialogMessage String) : Exception(errorMessage)
}

internal suspend fun BackendXValueModel.toXValueDto(): XValueDto {
  val xValueModel = this
  val xValue = this.xValue
  val valueMarkupFlow: RpcFlow<XValueMarkerDto?> = xValueModel.marker.toRpc()

  return XValueDto(
    xValueModel.id,
    xValue.xValueDescriptorAsync?.asDeferred(),
    canNavigateToSource = xValue.canNavigateToSource(),
    canNavigateToTypeSource = xValue.canNavigateToTypeSourceAsync().asDeferred(),
    canBeModified = xValue.modifierAsync.thenApply { modifier -> modifier != null }.asDeferred(),
    valueMarkupFlow
  )
}

internal fun newXValueModel(
  xValue: XValue,
  session: XDebugSessionImpl,
): BackendXValueModel {
  // TODO[IJPL-160146]: XValues should be stuck on suspension context coroutine scope, not on session one.
  val xValueCs = session.coroutineScope
  val xValueModel = BackendXValueModelsManager.getInstance(session.project).createXValueModel(xValueCs, session, xValue)
  return xValueModel.apply {
    setInitialMarker()
  }
}

private fun BackendXValueModel.setInitialMarker() {
  // TODO[IJPL-160146]: XValues should be stuck on suspension context coroutine scope, not on session one.
  session.coroutineScope.launch {
    xValue.isReady.await()
    val markers = session.valueMarkers
    val marker = markers?.getMarkup(xValue) ?: return@launch
    setMarker(marker)
  }
}

@Service(Service.Level.APP)
private class EvaluationCoroutineScopeProvider(val cs: CoroutineScope) {

  companion object {
    fun getInstance(): EvaluationCoroutineScopeProvider = service()
  }
}