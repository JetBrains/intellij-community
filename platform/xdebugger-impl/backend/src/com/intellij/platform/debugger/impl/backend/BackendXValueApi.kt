// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.rpc.PreloadChildrenEvent
import com.intellij.platform.debugger.impl.rpc.XContainerId
import com.intellij.platform.debugger.impl.rpc.XDebuggerHyperlinkId
import com.intellij.platform.debugger.impl.rpc.XDebuggerTreeExpandedNode
import com.intellij.platform.debugger.impl.rpc.XExpressionDto
import com.intellij.platform.debugger.impl.rpc.XFullValueEvaluatorResult
import com.intellij.platform.debugger.impl.rpc.XInlineDebuggerDataDto
import com.intellij.platform.debugger.impl.rpc.XSourcePositionDto
import com.intellij.platform.debugger.impl.rpc.XStackFrameId
import com.intellij.platform.debugger.impl.rpc.XValueApi
import com.intellij.platform.debugger.impl.rpc.XValueComputeChildrenEvent
import com.intellij.platform.debugger.impl.rpc.XValueGroupId
import com.intellij.platform.debugger.impl.rpc.XValueId
import com.intellij.platform.debugger.impl.rpc.XValueSerializedPresentation
import com.intellij.platform.debugger.impl.rpc.toRpc
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.containers.MultiMap
import com.intellij.xdebugger.Obsolescent
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XFullValueEvaluator.XFullValueEvaluationCallback
import com.intellij.xdebugger.frame.XInlineDebuggerDataCallback
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueContainer
import com.intellij.xdebugger.frame.XValueGroup
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.models.getOrStoreGlobally
import com.intellij.xdebugger.impl.rpc.models.toRpc
import com.intellij.xdebugger.impl.rpc.models.toXValueDto
import com.intellij.xdebugger.impl.rpc.toRpc
import fleet.rpc.core.toRpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.asCompletableFuture
import java.awt.Font
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JPanel

internal class BackendXValueApi : XValueApi {
  override suspend fun computeTooltipPresentation(xValueId: XValueId): Flow<XValueSerializedPresentation> {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return emptyFlow()
    return xValueModel.computeTooltipPresentation()
  }

  override fun computeChildren(id: XContainerId): Flow<XValueComputeChildrenEvent> {
    return computeChildrenInternal(id)
  }

  override fun computeExpandedChildren(frameId: XStackFrameId, root: XDebuggerTreeExpandedNode): Flow<PreloadChildrenEvent> {
    return channelFlow {
      processExpandedChildren(frameId, this, root)
    }
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

  override suspend fun nodeLinkClicked(linkId: XDebuggerHyperlinkId) {
    val link = linkId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      val dummyEvent = MouseEvent(JPanel(), 0, 0, 0, 0, 0, 1, false)
      link.onClick(dummyEvent)
    }
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

@Suppress("OPT_IN_USAGE")
private fun computeContainerChildren(
  parentCs: CoroutineScope,
  xValueContainer: XValueContainer,
  session: XDebugSessionImpl,
): Flow<XValueComputeChildrenEvent> {
  val rawEvents = Channel<RawComputeChildrenEvent>(capacity = Int.MAX_VALUE)

  return channelFlow {
    parentCs.awaitCancellationAndInvoke {
      rawEvents.close()
    }
    val addNextChildrenCallbackHandler = AddNextChildrenCallbackHandler(this@channelFlow)

    val xCompositeBridgeNode = object : XCompositeNode {
      @Volatile
      var obsolete = false
      override fun isObsolete(): Boolean {
        return obsolete
      }

      override fun addChildren(children: XValueChildrenList, last: Boolean) {
        rawEvents.trySend(RawComputeChildrenEvent.AddChildren(children, last, session))
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

    try {
      xValueContainer.computeChildren(xCompositeBridgeNode)
      for (event in rawEvents) {
        event.sendAsRpcEvents(parentCs, this@channelFlow)
      }
    }
    finally {
      xCompositeBridgeNode.obsolete = true
    }
  }
}

private sealed interface RawComputeChildrenEvent {
  suspend fun sendAsRpcEvents(parentCoroutineScope: CoroutineScope, channel: SendChannel<XValueComputeChildrenEvent>) {
    channel.send(convertToRpcEvent(parentCoroutineScope))
  }

  suspend fun convertToRpcEvent(parentCoroutineScope: CoroutineScope): XValueComputeChildrenEvent = error("Should not be called")


  data class AddChildren(val children: XValueChildrenList, val last: Boolean, val session: XDebugSessionImpl) : RawComputeChildrenEvent {
    override suspend fun sendAsRpcEvents(parentCoroutineScope: CoroutineScope, channel: SendChannel<XValueComputeChildrenEvent>) {
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

      val topValuesEntities = children.topValues.map {
        newChildXValueModel(it, parentCoroutineScope, session)
      }
      val topValues = topValuesEntities.map { it.toXValueDto() }

      channel.send(XValueComputeChildrenEvent.AddChildren(names, childrenXValueDtos, last, topGroups, bottomGroups, topValues))

      fun subscribeToPresentationsFlow(model: BackendXValueModel) {
        parentCoroutineScope.launch {
          model.presentation.collectLatest {
            channel.send(XValueComputeChildrenEvent.XValuePresentationEvent(model.id, it))
          }
        }
      }

      fun subscribeToFullValueFlow(model: BackendXValueModel) {
        parentCoroutineScope.launch {
          model.fullValueEvaluator.collectLatest {
            channel.send(XValueComputeChildrenEvent.XValueFullValueEvaluatorEvent(model.id, it?.toRpc()))
          }
        }
      }

      fun subscribeToAdditionalLinkFlow(model: BackendXValueModel) {
        parentCoroutineScope.launch {
          model.additionalLinkFlow.collectLatest {
            channel.send(XValueComputeChildrenEvent.XValueAdditionalLinkEvent(model.id, it?.toRpc(model.cs)))
          }
        }
      }

      childrenXValueEntities.forEach(::subscribeToPresentationsFlow)
      childrenXValueEntities.forEach(::subscribeToFullValueFlow)
      childrenXValueEntities.forEach(::subscribeToAdditionalLinkFlow)
      topValuesEntities.forEach(::subscribeToPresentationsFlow)
      topValuesEntities.forEach(::subscribeToFullValueFlow)
      topValuesEntities.forEach(::subscribeToAdditionalLinkFlow)
    }
  }

  data class SetAlreadySorted(val value: Boolean) : RawComputeChildrenEvent {
    override suspend fun convertToRpcEvent(parentCoroutineScope: CoroutineScope): XValueComputeChildrenEvent {
      return XValueComputeChildrenEvent.SetAlreadySorted(value)
    }
  }

  data class SetErrorMessage(val message: String, val link: XDebuggerTreeNodeHyperlink?) : RawComputeChildrenEvent {
    override suspend fun convertToRpcEvent(parentCoroutineScope: CoroutineScope): XValueComputeChildrenEvent {
      return XValueComputeChildrenEvent.SetErrorMessage(message, link?.toRpc(parentCoroutineScope))
    }
  }

  data class SetMessage(val message: String, val icon: Icon?, val attributes: SimpleTextAttributes, val link: XDebuggerTreeNodeHyperlink?) : RawComputeChildrenEvent {
    override suspend fun convertToRpcEvent(parentCoroutineScope: CoroutineScope): XValueComputeChildrenEvent {
      return XValueComputeChildrenEvent.SetMessage(message, icon?.rpcId(), attributes.rpcId(), link?.toRpc(parentCoroutineScope))
    }
  }

  data class TooManyChildren(val remaining: Int, val addNextChildren: Runnable?, val addNextChildrenCallbackHandler: AddNextChildrenCallbackHandler) : RawComputeChildrenEvent {
    override suspend fun convertToRpcEvent(parentCoroutineScope: CoroutineScope): XValueComputeChildrenEvent {
      return XValueComputeChildrenEvent.TooManyChildren(remaining, addNextChildrenCallbackHandler.setAddNextChildrenCallback(addNextChildren))
    }
  }
}

private fun computeChildrenInternal(containerId: XContainerId): Flow<XValueComputeChildrenEvent> {
  val (container, scope, session) = when (containerId) {
    is XStackFrameId -> {
      val stackFrameModel = containerId.findValue() ?: return emptyFlow()
      Triple(stackFrameModel.stackFrame, stackFrameModel.coroutineScope, stackFrameModel.session)
    }
    is XValueGroupId -> {
      val xGroupModel = containerId.findValue() ?: return emptyFlow()
      Triple(xGroupModel.xValueGroup, xGroupModel.cs, xGroupModel.session)
    }
    is XValueId -> {
      val xValueModel = BackendXValueModel.findById(containerId) ?: return emptyFlow()
      Triple(xValueModel.xValue, xValueModel.cs, xValueModel.session)
    }
  }
  return computeContainerChildren(scope, container, session)
}

/**
 * Match [XContainerId] children with [XDebuggerTreeExpandedNode] children, and start child computation if matched.
 */
private suspend fun processExpandedChildren(id: XContainerId, producerScope: ProducerScope<PreloadChildrenEvent>, root: XDebuggerTreeExpandedNode) {
  val name2Child = MultiMap<String, XDebuggerTreeExpandedNode>()
  for (child in root.children) {
    name2Child.putValue(child.name, child)
  }

  val childrenEventsFlow = computeChildrenInternal(id)
  childrenEventsFlow.collect { event ->
    val childrenToLoad = collectChildrenToLoad(event, name2Child)
    // notify which children are going to be preloaded
    for ((childId, _) in childrenToLoad) {
      producerScope.send(PreloadChildrenEvent.ToBePreloaded(childId))
    }
    // then pass the original event
    producerScope.send(PreloadChildrenEvent.ExpandedChildrenEvent(id, event))
    // and then start children loading
    for ((childId, node) in childrenToLoad) {
      producerScope.launch {
        processExpandedChildren(childId, producerScope, node)
      }
    }
  }
}

private fun collectChildrenToLoad(
  event: XValueComputeChildrenEvent,
  name2Child: MultiMap<String, XDebuggerTreeExpandedNode>,
): List<Pair<XContainerId, XDebuggerTreeExpandedNode>> {
  if (event !is XValueComputeChildrenEvent.AddChildren) return emptyList()
  val children = mutableListOf<Pair<XContainerId, XDebuggerTreeExpandedNode>>()
  val namedContainers = collectNamedChildren(event)

  for ((name, childId) in namedContainers) {
    val nodes = name2Child.get(name)
    val node = nodes.firstOrNull() ?: continue
    nodes.remove(node)

    children += childId to node
  }
  return children
}

private fun collectNamedChildren(event: XValueComputeChildrenEvent.AddChildren): List<Pair<String, XContainerId>> {
  val nameAndId = mutableListOf<Pair<String, XContainerId>>()
  for (group in event.topGroups) {
    nameAndId += group.groupName to group.id
  }
  for (value in event.topValues + event.children) {
    val name = value.name ?: continue
    nameAndId += name to value.id
  }
  for (group in event.bottomGroups) {
    nameAndId += group.groupName to group.id
  }
  return nameAndId
}

private fun <T> ReceiveChannel<T>.asColdFlow(): Flow<T> = flow {
  consumeEach {
    emit(it)
  }
}
