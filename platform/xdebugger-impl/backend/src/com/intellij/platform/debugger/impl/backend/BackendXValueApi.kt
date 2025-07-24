// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.Obsolescent
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.XFullValueEvaluator.XFullValueEvaluationCallback
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XValueGroupId
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.models.getOrStoreGlobally
import fleet.rpc.core.toRpc
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import org.jetbrains.concurrency.asCompletableFuture
import java.awt.Font
import javax.swing.Icon

internal class BackendXValueApi : XValueApi {
  override suspend fun computeTooltipPresentation(xValueId: XValueId): Flow<XValueSerializedPresentation> {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return emptyFlow()
    return xValueModel.computeTooltipPresentation()
  }

  override suspend fun computeChildren(xValueId: XValueId): Flow<XValueComputeChildrenEvent> {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return emptyFlow()
    return computeContainerChildren(xValueModel.cs, xValueModel.xValue, xValueModel.session)
  }

  override suspend fun computeXValueGroupChildren(xValueGroupId: XValueGroupId): Flow<XValueComputeChildrenEvent> {
    val xValueModel = xValueGroupId.findValue() ?: return emptyFlow()
    return computeContainerChildren(xValueModel.cs, xValueModel.xValueGroup, xValueModel.session)
  }

  override suspend fun disposeXValue(xValueId: XValueId) {
    BackendXValueModel.findById(xValueId)?.delete()
  }

  /**
   * Implementation note:
   *
   * The return value might be simplified to a single [XFullValueEvaluatorResult],
   * but the [com.intellij.xdebugger.frame.XFullValueEvaluator.XFullValueEvaluationCallback.evaluated] method might be called multiple times.
   */
  override suspend fun evaluateFullValue(xValueId: XValueId): Flow<XFullValueEvaluatorResult> = channelFlow {
    val xValueModel = BackendXValueModel.findById(xValueId)
    if (xValueModel == null) {
      send(XFullValueEvaluatorResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.full.value.evaluator.not.available")))
      return@channelFlow
    }
    val xFullValueEvaluator = xValueModel.fullValueEvaluator.value
    if (xFullValueEvaluator == null) {
      send(XFullValueEvaluatorResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.full.value.evaluator.not.available")))
      return@channelFlow
    }

    var isObsolete = false

    val callback = object : XFullValueEvaluationCallback, Obsolescent {
      override fun isObsolete(): Boolean {
        return isObsolete
      }

      override fun evaluated(fullValue: String) {
        trySend(XFullValueEvaluatorResult.Evaluated(fullValue))
      }

      override fun evaluated(fullValue: String, font: Font?) {
        // TODO[IJPL-160146]: support Font?
        trySend(XFullValueEvaluatorResult.Evaluated(fullValue))
      }

      override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
        trySend(XFullValueEvaluatorResult.EvaluationError(errorMessage))
      }
    }

    xFullValueEvaluator.startEvaluation(callback)

    awaitClose {
      isObsolete = true
    }
  }.buffer(Channel.UNLIMITED)

  override suspend fun computeExpression(xValueId: XValueId): XExpressionDto? {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return null
    val xExpression = xValueModel.xValue.calculateEvaluationExpression().asCompletableFuture().await() ?: return null
    return xExpression.toRpc()
  }

  override suspend fun computeSourcePosition(xValueId: XValueId): XSourcePositionDto? {
    return computePosition(xValueId) { xValue, navigatable ->
      xValue.computeSourcePosition(navigatable)
    }
  }

  override suspend fun computeTypeSourcePosition(xValueId: XValueId): XSourcePositionDto? {
    return computePosition(xValueId) { xValue, navigatable ->
      xValue.computeTypeSourcePosition(navigatable)
    }
  }

  private suspend fun computePosition(xValueId: XValueId, compute: (XValue, XNavigatable) -> Unit): XSourcePositionDto? {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return null
    val sourcePosition = computeSourcePositionWithTimeout { navigatable ->
      compute(xValueModel.xValue, navigatable)
    }
    return sourcePosition?.toRpc()
  }

  override suspend fun computeInlineData(xValueId: XValueId): XInlineDebuggerDataDto? {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return null
    val channel = Channel<XSourcePositionDto>(Channel.UNLIMITED)
    val state = xValueModel.xValue.computeInlineDebuggerData(object : XInlineDebuggerDataCallback() {
      override fun computed(position: XSourcePosition?) {
        if (position == null) return
        channel.trySend(position.toRpc())
      }
    })
    return XInlineDebuggerDataDto(state, channel.asColdFlow().toRpc())
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

internal fun computeContainerChildren(
  parentCs: CoroutineScope,
  xValueContainer: XValueContainer,
  session: XDebugSessionImpl,
): Flow<XValueComputeChildrenEvent> {
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

    xValueContainer.computeChildren(xCompositeBridgeNode)

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
      send(event.convertToRpcEvent(parentCs, session))
    }
  }
}

private sealed interface RawComputeChildrenEvent {
  suspend fun convertToRpcEvent(parentCoroutineScope: CoroutineScope, session: XDebugSessionImpl): XValueComputeChildrenEvent

  data class AddChildren(val children: XValueChildrenList, val last: Boolean) : RawComputeChildrenEvent {
    override suspend fun convertToRpcEvent(parentCoroutineScope: CoroutineScope, session: XDebugSessionImpl): XValueComputeChildrenEvent {
      val names = (0 until children.size()).map { children.getName(it) }
      val childrenXValues = (0 until children.size()).map { children.getValue(it) }
      val childrenXValueEntities = childrenXValues.map { childXValue ->
        newChildXValueModel(childXValue, parentCoroutineScope, session)
      }
      val childrenXValueDtos = coroutineScope {
        childrenXValueEntities.map { childXValueEntity ->
          async {
            childXValueEntity.toXValueDto()
          }
        }
      }.awaitAll()

      fun List<XValueGroup>.toDto() = map {
        it.getOrStoreGlobally(parentCoroutineScope, session).toXValueGroupDto()
      }

      val topGroups = children.topGroups.toDto()
      val bottomGroups = children.bottomGroups.toDto()

      return XValueComputeChildrenEvent.AddChildren(names, childrenXValueDtos, last, topGroups, bottomGroups)
    }
  }

  data class SetAlreadySorted(val value: Boolean) : RawComputeChildrenEvent {
    override suspend fun convertToRpcEvent(parentCoroutineScope: CoroutineScope, session: XDebugSessionImpl): XValueComputeChildrenEvent {
      return XValueComputeChildrenEvent.SetAlreadySorted(value)
    }
  }

  data class SetErrorMessage(val message: String, val link: XDebuggerTreeNodeHyperlink?) : RawComputeChildrenEvent {
    override suspend fun convertToRpcEvent(parentCoroutineScope: CoroutineScope, session: XDebugSessionImpl): XValueComputeChildrenEvent {
      // TODO[IJPL-160146]: support XDebuggerTreeNodeHyperlink serialization
      return XValueComputeChildrenEvent.SetErrorMessage(message, link)
    }
  }

  data class SetMessage(val message: String, val icon: Icon?, val attributes: SimpleTextAttributes?, val link: XDebuggerTreeNodeHyperlink?) : RawComputeChildrenEvent {
    override suspend fun convertToRpcEvent(parentCoroutineScope: CoroutineScope, session: XDebugSessionImpl): XValueComputeChildrenEvent {
      // TODO[IJPL-160146]: support SimpleTextAttributes serialization
      // TODO[IJPL-160146]: support XDebuggerTreeNodeHyperlink serialization
      return XValueComputeChildrenEvent.SetMessage(message, icon?.rpcId(), attributes, link)
    }
  }

  data class TooManyChildren(val remaining: Int, val addNextChildren: Runnable?, val addNextChildrenCallbackHandler: AddNextChildrenCallbackHandler) : RawComputeChildrenEvent {
    override suspend fun convertToRpcEvent(parentCoroutineScope: CoroutineScope, session: XDebugSessionImpl): XValueComputeChildrenEvent {
      return XValueComputeChildrenEvent.TooManyChildren(remaining, addNextChildrenCallbackHandler.setAddNextChildrenCallback(addNextChildren))
    }
  }
}

private fun <T> ReceiveChannel<T>.asColdFlow(): Flow<T> = flow {
  consumeEach {
    emit(it)
  }
}
