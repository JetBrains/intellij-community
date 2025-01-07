// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.DocumentId
import com.intellij.ide.rpc.document
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.kernel.backend.*
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.ui.tree.nodes.XValuePresentationUtil.XValuePresentationTextExtractor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

internal class BackendXDebuggerEvaluatorApi : XDebuggerEvaluatorApi {
  override suspend fun evaluate(evaluatorDto: XDebuggerEvaluatorDto, expression: String): Deferred<XEvaluationResult> {
    return evaluate(evaluatorDto) { evaluator, callback ->
      // TODO[IJPL-160146]: pass XSourcePosition
      evaluator.evaluate(expression, callback, null)
    }
  }

  override suspend fun evaluateInDocument(evaluatorDto: XDebuggerEvaluatorDto, documentId: DocumentId, offset: Int, type: ValueHintType): Deferred<XEvaluationResult> {
    return evaluate(evaluatorDto) { evaluator, callback ->
      val document = documentId.document()!!
      if (evaluator is XDebuggerDocumentOffsetEvaluator) {
        evaluator.evaluate(document, offset, type, callback)
      }
      else {
        callback.errorOccurred(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"))
      }
    }
  }

  override suspend fun disposeXValue(xValueId: XValueId) {
    val xValueEntity = xValueId.eid.findValueEntity<XValue>() ?: return
    xValueEntity.delete()
  }

  private suspend fun evaluate(
    evaluatorDto: XDebuggerEvaluatorDto,
    evaluateFun: suspend (XDebuggerEvaluator, XEvaluationCallback) -> Unit,
  ): Deferred<XEvaluationResult> {
    val evaluator = evaluatorDto.eid.findValueEntity<XDebuggerEvaluator>()?.value
                    ?: return CompletableDeferred(XEvaluationResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.no.evaluator.id")))
    val evaluationResult = CompletableDeferred<XValue>()

    withContext(Dispatchers.EDT) {
      // TODO[IJPL-160146]: pass XSourcePosition
      val callback = object : XEvaluationCallback {
        override fun evaluated(result: XValue) {
          evaluationResult.complete(result)
        }

        override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
          evaluationResult.completeExceptionally(EvaluationException(errorMessage))
        }
      }
      evaluateFun(evaluator, callback)
    }
    val evaluationCoroutineScope = EvaluationCoroutineScopeProvider.getInstance().cs

    return evaluationCoroutineScope.async(Dispatchers.EDT) {
      val xValue = try {
        evaluationResult.await()
      }
      catch (e: EvaluationException) {
        return@async XEvaluationResult.EvaluationError(e.errorMessage)
      }
      val xValueEntity = newValueEntity(xValue)
      XEvaluationResult.Evaluated(XValueId(xValueEntity.id))
    }
  }

  private class EvaluationException(val errorMessage: @NlsContexts.DialogMessage String) : Exception(errorMessage)

  override suspend fun computePresentation(xValueId: XValueId, xValuePlace: XValuePlace): Flow<XValuePresentation>? {
    val xValueEntity = xValueId.eid.findValueEntity<XValue>() ?: return emptyFlow()
    val presentations = Channel<XValuePresentation>(capacity = Int.MAX_VALUE)
    val xValue = xValueEntity.value
    return channelFlow {
      var isObsolete = false

      val valueNode = object : XValueNode {
        override fun isObsolete(): Boolean {
          return isObsolete
        }

        override fun setPresentation(icon: Icon?, type: @NonNls String?, value: @NonNls String, hasChildren: Boolean) {
          presentations.trySend(XValuePresentation(icon?.rpcId(), type, value, hasChildren))
        }

        override fun setPresentation(icon: Icon?, presentation: com.intellij.xdebugger.frame.presentation.XValuePresentation, hasChildren: Boolean) {
          // TODO[IJPL-160146]: handle XValuePresentation fully
          val textExtractor = XValuePresentationTextExtractor()
          presentation.renderValue(textExtractor)
          setPresentation(icon, presentation.type, textExtractor.text, hasChildren)
        }

        override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {
          // TODO[IJPL-160146]: implement setFullValueEvaluator
        }
      }
      xValue.computePresentation(valueNode, xValuePlace)

      launch {
        try {
          awaitCancellation()
        }
        finally {
          isObsolete = true
        }
      }

      for (presentation in presentations) {
        send(presentation)
      }
    }
  }

  override suspend fun computeChildren(xValueId: XValueId): Flow<XValueComputeChildrenEvent>? {
    val xValueEntity = xValueId.eid.findValueEntity<XValue>() ?: return emptyFlow()
    val rawEvents = Channel<RawComputeChildrenEvent>(capacity = Int.MAX_VALUE)
    val xValue = xValueEntity.value

    var isObsolete = false
    val xCompositeBridgeNode = object : XCompositeNode {
      override fun isObsolete(): Boolean {
        return isObsolete
      }

      override fun addChildren(children: XValueChildrenList, last: Boolean) {
        rawEvents.trySend(RawComputeChildrenEvent.AddChildren(children, last))
      }

      override fun tooManyChildren(remaining: Int) {
        rawEvents.trySend(RawComputeChildrenEvent.TooManyChildren(remaining, null))
      }

      override fun tooManyChildren(remaining: Int, addNextChildren: Runnable) {
        rawEvents.trySend(RawComputeChildrenEvent.TooManyChildren(remaining, addNextChildren))
      }

      override fun setAlreadySorted(alreadySorted: Boolean) {
        rawEvents.trySend(RawComputeChildrenEvent.SetAlreadySorted(alreadySorted))
      }

      override fun setErrorMessage(errorMessage: String) {
        rawEvents.trySend(RawComputeChildrenEvent.SetErrorMessage(errorMessage, null))
      }

      override fun setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink?) {
        rawEvents.trySend(RawComputeChildrenEvent.SetErrorMessage(errorMessage, link))
      }

      override fun setMessage(message: String, icon: Icon?, attributes: SimpleTextAttributes, link: XDebuggerTreeNodeHyperlink?) {
        rawEvents.trySend(RawComputeChildrenEvent.SetMessage(message, icon, attributes, link))
      }
    }

    xValue.computeChildren(xCompositeBridgeNode)

    return channelFlow {
      // mark xCompositeBridgeNode as obsolete when the channel collection is canceled
      launch {
        try {
          awaitCancellation()
        }
        finally {
          isObsolete = true
        }
      }

      for (event in rawEvents) {
        send(event.convertToRpcEvent(xValueEntity))
      }
    }
  }

  private sealed interface RawComputeChildrenEvent {
    suspend fun convertToRpcEvent(parentXValueEntity: BackendValueEntity<XValue>): XValueComputeChildrenEvent

    data class AddChildren(val children: XValueChildrenList, val last: Boolean) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: BackendValueEntity<XValue>): XValueComputeChildrenEvent {
        val names = (0 until children.size()).map { children.getName(it) }
        val childrenXValues = (0 until children.size()).map { children.getValue(it) }
        val childrenXValueEntities = childrenXValues.map { childXValue ->
          newValueEntity(childXValue).apply {
            cascadeDeleteBy(parentXValueEntity)
          }
        }
        val childrenXValueIds = childrenXValueEntities.map { XValueId(it.id) }
        return XValueComputeChildrenEvent.AddChildren(names, childrenXValueIds, last)
      }
    }

    data class SetAlreadySorted(val value: Boolean) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: BackendValueEntity<XValue>): XValueComputeChildrenEvent {
        return XValueComputeChildrenEvent.SetAlreadySorted(value)
      }
    }

    data class SetErrorMessage(val message: String, val link: XDebuggerTreeNodeHyperlink?) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: BackendValueEntity<XValue>): XValueComputeChildrenEvent {
        // TODO[IJPL-160146]: support XDebuggerTreeNodeHyperlink serialization
        return XValueComputeChildrenEvent.SetErrorMessage(message, link)
      }
    }

    data class SetMessage(val message: String, val icon: Icon?, val attributes: SimpleTextAttributes?, val link: XDebuggerTreeNodeHyperlink?) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: BackendValueEntity<XValue>): XValueComputeChildrenEvent {
        // TODO[IJPL-160146]: support SimpleTextAttributes serialization
        // TODO[IJPL-160146]: support XDebuggerTreeNodeHyperlink serialization
        return XValueComputeChildrenEvent.SetMessage(message, icon?.rpcId(), attributes, link)
      }
    }

    data class TooManyChildren(val remaining: Int, val addNextChildren: Runnable?) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: BackendValueEntity<XValue>): XValueComputeChildrenEvent {
        // TODO[IJPL-160146]: support addNextChildren serialization
        return XValueComputeChildrenEvent.TooManyChildren(remaining, addNextChildren)
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