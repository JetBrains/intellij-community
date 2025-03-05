// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.DocumentId
import com.intellij.ide.rpc.document
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.project.asProject
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rhizome.XDebuggerEvaluatorEntity
import com.intellij.xdebugger.impl.rhizome.XValueEntity
import com.intellij.xdebugger.impl.rhizome.XValueMarkerDto
import com.intellij.xdebugger.impl.rpc.*
import com.jetbrains.rhizomedb.entity
import fleet.kernel.change
import fleet.kernel.rete.collect
import fleet.kernel.rete.query
import fleet.kernel.tryWithEntities
import fleet.kernel.withEntities
import fleet.rpc.core.toRpc
import fleet.util.UID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import org.jetbrains.concurrency.asDeferred

internal class BackendXDebuggerEvaluatorApi : XDebuggerEvaluatorApi {
  override suspend fun evaluate(evaluatorId: XDebuggerEvaluatorId, expression: String, position: XSourcePositionDto?): Deferred<XEvaluationResult> {
    return evaluate(evaluatorId) { project, evaluator, callback ->
      evaluator.evaluate(expression, callback, position?.sourcePosition())
    }
  }

  override suspend fun evaluateXExpression(evaluatorId: XDebuggerEvaluatorId, expression: XExpressionDto, position: XSourcePositionDto?): Deferred<XEvaluationResult> {
    return evaluate(evaluatorId) { project, evaluator, callback ->
      evaluator.evaluate(expression.xExpression(), callback, position?.sourcePosition())
    }
  }

  override suspend fun evaluateInDocument(evaluatorId: XDebuggerEvaluatorId, documentId: DocumentId, offset: Int, type: ValueHintType): Deferred<XEvaluationResult> {
    return evaluate(evaluatorId) { project, evaluator, callback ->
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
    evaluatorId: XDebuggerEvaluatorId,
    evaluateFun: suspend (Project, XDebuggerEvaluator, XEvaluationCallback) -> Unit,
  ): Deferred<XEvaluationResult> {
    val evaluatorEntity = entity(XDebuggerEvaluatorEntity.EvaluatorId, evaluatorId)
                          ?: return CompletableDeferred(XEvaluationResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.no.evaluator.id")))
    val evaluator = evaluatorEntity.evaluator
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
      evaluateFun(evaluatorEntity.sessionEntity.projectEntity.asProject(), evaluator, callback)
    }
    val evaluationCoroutineScope = EvaluationCoroutineScopeProvider.getInstance().cs

    return evaluationCoroutineScope.async(Dispatchers.EDT) {
      val xValue = try {
        evaluationResult.await()
      }
      catch (e: EvaluationException) {
        return@async XEvaluationResult.EvaluationError(e.errorMessage)
      }
      val xValueEntity = newXValueEntity(xValue, evaluatorEntity)
      val xValueDto = xValueEntity.toXValueDto()
      XEvaluationResult.Evaluated(xValueDto)
    }
  }

  private class EvaluationException(val errorMessage: @NlsContexts.DialogMessage String) : Exception(errorMessage)
}

internal suspend fun XValueEntity.toXValueDto(): XValueDto {
  val xValueEntity = this
  val xValue = this.xValue
  val valueMarkupFlow = channelFlow<XValueMarkerDto?> {
    tryWithEntities(xValueEntity) {
      query { xValueEntity.marker }.collect {
        send(it)
      }
    }
  }.toRpc()

  return XValueDto(
    xValueId,
    xValue.xValueDescriptorAsync?.asDeferred(),
    canNavigateToSource = xValue.canNavigateToSource(),
    canNavigateToTypeSource = xValue.canNavigateToTypeSourceAsync().asDeferred(),
    canBeModified = xValue.modifierAsync.thenApply { modifier -> modifier != null }.asDeferred(),
    valueMarkupFlow
  )
}

private suspend fun newXValueEntity(
  xValue: XValue,
  evaluatorEntity: XDebuggerEvaluatorEntity,
): XValueEntity {
  val xValueEntity = change {
    XValueEntity.new {
      it[XValueEntity.XValueId] = XValueId(UID.random())
      it[XValueEntity.XValueAttribute] = xValue
      it[XValueEntity.SessionEntity] = evaluatorEntity.sessionEntity
    }
  }
  return xValueEntity.apply {
    setInitialMarker()
  }
}

internal suspend fun newChildXValueEntity(
  xValue: XValue,
  parentXValue: XValueEntity,
): XValueEntity {
  val xValueEntity = change {
    XValueEntity.new {
      it[XValueEntity.XValueId] = XValueId(UID.random())
      it[XValueEntity.XValueAttribute] = xValue
      it[XValueEntity.SessionEntity] = parentXValue.sessionEntity
      it[XValueEntity.ParentXValue] = parentXValue
    }
  }
  return xValueEntity.apply {
    setInitialMarker()
  }
}

private fun XValueEntity.setInitialMarker() {
  val xValueEntity = this
  val session = sessionEntity.session
  (session as XDebugSessionImpl).coroutineScope.launch {
    withEntities(xValueEntity) {
      xValue.isReady.await()
      val markers = session.valueMarkers
      val marker = markers?.getMarkup(xValue) ?: return@withEntities
      change {
        xValueEntity.update {
          it[XValueEntity.Marker] = XValueMarkerDto(marker.text, marker.color, marker.toolTipText)
        }
      }
    }
  }
}

@Service(Service.Level.APP)
private class EvaluationCoroutineScopeProvider(val cs: CoroutineScope) {

  companion object {
    fun getInstance(): EvaluationCoroutineScopeProvider = service()
  }
}