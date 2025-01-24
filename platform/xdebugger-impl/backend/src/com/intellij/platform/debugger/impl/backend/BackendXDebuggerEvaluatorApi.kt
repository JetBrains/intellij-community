// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.DocumentId
import com.intellij.ide.rpc.document
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.kernel.backend.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.Obsolescent
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.XFullValueEvaluator.XFullValueEvaluationCallback
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.frame.presentation.XValuePresentation.XValueTextRenderer
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rhizome.XDebuggerEvaluatorEntity
import com.intellij.xdebugger.impl.rhizome.XValueEntity
import com.intellij.xdebugger.impl.rhizome.XValueMarkerDto
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.rpc.XFullValueEvaluatorDto.FullValueEvaluatorLinkAttributes
import com.intellij.xdebugger.impl.ui.CustomComponentEvaluator
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeEx
import com.jetbrains.rhizomedb.entity
import fleet.kernel.change
import fleet.kernel.rete.collect
import fleet.kernel.rete.query
import fleet.kernel.tryWithEntities
import fleet.kernel.withEntities
import fleet.rpc.core.toRpc
import fleet.util.UID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.NonNls
import java.awt.Font
import javax.swing.Icon

internal class BackendXDebuggerEvaluatorApi : XDebuggerEvaluatorApi {
  override suspend fun evaluate(evaluatorId: XDebuggerEvaluatorId, expression: String, position: XSourcePositionDto?): Deferred<XEvaluationResult> {
    return evaluate(evaluatorId) { evaluator, callback ->
      evaluator.evaluate(expression, callback, position?.sourcePosition())
    }
  }

  override suspend fun evaluateXExpression(evaluatorId: XDebuggerEvaluatorId, expression: XExpressionDto, position: XSourcePositionDto?): Deferred<XEvaluationResult> {
    return evaluate(evaluatorId) { evaluator, callback ->
      evaluator.evaluate(expression.xExpression(), callback, position?.sourcePosition())
    }
  }

  override suspend fun evaluateInDocument(evaluatorId: XDebuggerEvaluatorId, documentId: DocumentId, offset: Int, type: ValueHintType): Deferred<XEvaluationResult> {
    return evaluate(evaluatorId) { evaluator, callback ->
      val document = documentId.document()!!
      if (evaluator is XDebuggerDocumentOffsetEvaluator) {
        evaluator.evaluate(document, offset, type, callback)
      }
      else {
        callback.errorOccurred(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"))
      }
    }
  }

  override suspend fun evaluateFullValue(fullValueEvaluatorId: XFullValueEvaluatorId): Deferred<XFullValueEvaluatorResult> {
    val xFullValueEvaluator = fullValueEvaluatorId.eid.findValueEntity<XFullValueEvaluator>()?.value
                              ?: return CompletableDeferred(XFullValueEvaluatorResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.full.value.evaluator.not.available")))

    val result = CompletableDeferred<XFullValueEvaluatorResult>()
    var isObsolete = false

    val callback = object : XFullValueEvaluationCallback, Obsolescent {
      override fun isObsolete(): Boolean {
        return isObsolete
      }

      override fun evaluated(fullValue: String) {
        result.complete(XFullValueEvaluatorResult.Evaluated(fullValue))
      }

      override fun evaluated(fullValue: String, font: Font?) {
        // TODO[IJPL-160146]: support Font?
        result.complete(XFullValueEvaluatorResult.Evaluated(fullValue))
      }

      override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
        result.complete(XFullValueEvaluatorResult.EvaluationError(errorMessage))
      }
    }

    result.invokeOnCompletion {
      isObsolete = true
    }

    xFullValueEvaluator.startEvaluation(callback)

    return result
  }

  override suspend fun disposeXValue(xValueId: XValueId) {
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return
    withContext(NonCancellable) {
      change {
        xValueEntity.delete()
      }
    }
  }

  private suspend fun evaluate(
    evaluatorId: XDebuggerEvaluatorId,
    evaluateFun: suspend (XDebuggerEvaluator, XEvaluationCallback) -> Unit,
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
      val xValueEntity = newXValueEntity(xValue, evaluatorEntity)
      val xValueDto = xValueEntity.toXValueDto()
      XEvaluationResult.Evaluated(xValueDto)
    }
  }

  private class EvaluationException(val errorMessage: @NlsContexts.DialogMessage String) : Exception(errorMessage)

  override suspend fun computePresentation(xValueId: XValueId, xValuePlace: XValuePlace): Flow<XValuePresentationEvent>? {
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return emptyFlow()
    val xValue = xValueEntity.xValue
    val presentations = Channel<XValuePresentationEvent>(capacity = Int.MAX_VALUE)
    return channelFlow {
      val channelCs = this@channelFlow as CoroutineScope
      var isObsolete = false

      val valueNode = object : XValueNodeEx {
        override fun isObsolete(): Boolean {
          return isObsolete
        }

        override fun setPresentation(icon: Icon?, type: @NonNls String?, value: @NonNls String, hasChildren: Boolean) {
          presentations.trySend(XValuePresentationEvent.SetSimplePresentation(icon?.rpcId(), type, value, hasChildren))
        }

        override fun setPresentation(icon: Icon?, presentation: XValuePresentation, hasChildren: Boolean) {
          val partsCollector = XValueTextRendererPartsCollector()
          presentation.renderValue(partsCollector)

          presentations.trySend(XValuePresentationEvent.SetAdvancedPresentation(
            icon?.rpcId(), hasChildren,
            presentation.separator, presentation.isShowName, presentation.type, presentation.isAsync,
            partsCollector.parts
          ))
        }

        override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {
          if (fullValueEvaluator is CustomComponentEvaluator) {
            // TODO[IJPL-160146]: support CustomComponentEvaluator
            return
          }

          channelCs.launch {
            // TODO[IJPL-160146]: dispose full value evaluator
            val fullValueEvaluatorEntity = newValueEntity(fullValueEvaluator)

            presentations.trySend(
              XValuePresentationEvent.SetFullValueEvaluator(
                XFullValueEvaluatorDto(
                  XFullValueEvaluatorId(fullValueEvaluatorEntity.id),
                  fullValueEvaluator.linkText,
                  fullValueEvaluator.isEnabled,
                  fullValueEvaluator.isShowValuePopup,
                  fullValueEvaluator.linkAttributes?.let {
                    FullValueEvaluatorLinkAttributes(it.linkIcon?.rpcId(), it.linkTooltipText, it.shortcutSupplier?.get())
                  }
                ))
            )
          }
        }

        override fun getXValue(): XValue {
          return xValue
        }

        override fun clearFullValueEvaluator() {
          // TODO[IJPL-160146]: data race with setFullValueEvaluator
          presentations.trySend(XValuePresentationEvent.ClearFullValueEvaluator)
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
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return emptyFlow()
    val xValue = xValueEntity.xValue
    val rawEvents = Channel<RawComputeChildrenEvent>(capacity = Int.MAX_VALUE)

    return channelFlow {
      val addNextChildrenCallbackHandler = AddNextChildrenCallbackHandler(this@channelFlow)

      var isObsolete = false
      val xCompositeBridgeNode = object : XCompositeNode {
        override fun isObsolete(): Boolean {
          return isObsolete
        }

        override fun addChildren(children: XValueChildrenList, last: Boolean) {
          rawEvents.trySend(RawComputeChildrenEvent.AddChildren(children, last))
        }

        override fun tooManyChildren(remaining: Int) {
          rawEvents.trySend(RawComputeChildrenEvent.TooManyChildren(remaining, null, addNextChildrenCallbackHandler))
        }

        override fun tooManyChildren(remaining: Int, addNextChildren: Runnable) {
          rawEvents.trySend(RawComputeChildrenEvent.TooManyChildren(remaining, addNextChildren, addNextChildrenCallbackHandler))
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

  private class XValueTextRendererPartsCollector : XValueTextRenderer {
    private val _parts = mutableListOf<XValueAdvancedPresentationPart>()

    val parts: List<XValueAdvancedPresentationPart>
      get() = _parts

    override fun renderValue(value: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.Value(value))
    }

    override fun renderStringValue(value: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.StringValue(value))
    }

    override fun renderNumericValue(value: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.NumericValue(value))
    }

    override fun renderKeywordValue(value: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.KeywordValue(value))
    }

    override fun renderValue(value: @NlsSafe String, key: TextAttributesKey) {
      _parts.add(XValueAdvancedPresentationPart.ValueWithAttributes(value, key))
    }

    override fun renderStringValue(value: @NlsSafe String, additionalSpecialCharsToHighlight: @NlsSafe String?, maxLength: Int) {
      _parts.add(XValueAdvancedPresentationPart.StringValueWithHighlighting(value, additionalSpecialCharsToHighlight, maxLength))
    }

    override fun renderComment(comment: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.Comment(comment))
    }

    override fun renderSpecialSymbol(symbol: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.SpecialSymbol(symbol))
    }

    override fun renderError(error: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.Error(error))
    }
  }

  private class AddNextChildrenCallbackHandler(cs: CoroutineScope) {
    private val cs = cs.childScope("AddNextChildrenCallbackHandler")
    private var currentChannelSubscription: Job? = null

    /**
     * Returns Channel which may be passed to frontend, so later on frontend may trigger backend's callbacks
     *
     * This method should be called sequentially
     */
    fun setAddNextChildrenCallback(callback: Runnable?): SendChannel<Unit>? {
      if (callback == null) {
        currentChannelSubscription?.cancel()
        currentChannelSubscription = null
        return null
      }

      val newChannel = Channel<Unit>(capacity = 1)

      val newChannelSubscription = cs.launch {
        launch {
          try {
            awaitCancellation()
          }
          finally {
            newChannel.close()
          }
        }

        for (addNextChildrenRequest in newChannel) {
          callback.run()
        }
      }

      currentChannelSubscription?.cancel()
      currentChannelSubscription = newChannelSubscription

      return newChannel
    }
  }

  private sealed interface RawComputeChildrenEvent {
    suspend fun convertToRpcEvent(parentXValueEntity: XValueEntity): XValueComputeChildrenEvent

    data class AddChildren(val children: XValueChildrenList, val last: Boolean) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: XValueEntity): XValueComputeChildrenEvent {
        val names = (0 until children.size()).map { children.getName(it) }
        val childrenXValues = (0 until children.size()).map { children.getValue(it) }
        val childrenXValueEntities = childrenXValues.map { childXValue ->
          newChildXValueEntity(childXValue, parentXValueEntity)
        }
        val childrenXValueDtos = childrenXValueEntities.map { childXValueEntity ->
          childXValueEntity.toXValueDto()
        }
        return XValueComputeChildrenEvent.AddChildren(names, childrenXValueDtos, last)
      }
    }

    data class SetAlreadySorted(val value: Boolean) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: XValueEntity): XValueComputeChildrenEvent {
        return XValueComputeChildrenEvent.SetAlreadySorted(value)
      }
    }

    data class SetErrorMessage(val message: String, val link: XDebuggerTreeNodeHyperlink?) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: XValueEntity): XValueComputeChildrenEvent {
        // TODO[IJPL-160146]: support XDebuggerTreeNodeHyperlink serialization
        return XValueComputeChildrenEvent.SetErrorMessage(message, link)
      }
    }

    data class SetMessage(val message: String, val icon: Icon?, val attributes: SimpleTextAttributes?, val link: XDebuggerTreeNodeHyperlink?) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: XValueEntity): XValueComputeChildrenEvent {
        // TODO[IJPL-160146]: support SimpleTextAttributes serialization
        // TODO[IJPL-160146]: support XDebuggerTreeNodeHyperlink serialization
        return XValueComputeChildrenEvent.SetMessage(message, icon?.rpcId(), attributes, link)
      }
    }

    data class TooManyChildren(val remaining: Int, val addNextChildren: Runnable?, val addNextChildrenCallbackHandler: AddNextChildrenCallbackHandler) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: XValueEntity): XValueComputeChildrenEvent {
        return XValueComputeChildrenEvent.TooManyChildren(remaining, addNextChildrenCallbackHandler.setAddNextChildrenCallback(addNextChildren))
      }
    }
  }
}

private suspend fun XValueEntity.toXValueDto(): XValueDto {
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

private suspend fun newChildXValueEntity(
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