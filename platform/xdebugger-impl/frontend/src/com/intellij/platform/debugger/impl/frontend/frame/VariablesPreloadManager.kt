// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.debugger.impl.rpc.PreloadChildrenEvent
import com.intellij.platform.debugger.impl.rpc.XContainerId
import com.intellij.platform.debugger.impl.rpc.XDebuggerTreeExpandedNode
import com.intellij.platform.debugger.impl.rpc.XStackFrameId
import com.intellij.platform.debugger.impl.rpc.XValueApi
import com.intellij.platform.debugger.impl.rpc.XValueComputeChildrenEvent
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap


/**
 * Preloads the variables in the tree described by [XDebuggerTreeExpandedNode].
 */
@OptIn(AwaitCancellationAndInvoke::class)
@VisibleForTesting
@ApiStatus.Internal
class VariablesPreloadManager(
  parentScope: CoroutineScope,
  treeState: XDebuggerTreeExpandedNode,
  frameId: XStackFrameId,
) {
  private val preloadedEvents = ConcurrentHashMap<XContainerId, Channel<XValueComputeChildrenEvent>>()

  init {
    val localEvents = hashMapOf<XContainerId, Channel<XValueComputeChildrenEvent>>()
    markToBeLoaded(frameId, localEvents)
    parentScope.launch {
      XValueApi.getInstance().computeExpandedChildren(frameId, treeState).collect { event ->
        when (event) {
          is PreloadChildrenEvent.ToBePreloaded -> {
            markToBeLoaded(event.id, localEvents)
          }
          is PreloadChildrenEvent.ExpandedChildrenEvent -> {
            val channel = localEvents[event.id] ?: run {
              fileLogger().error("Preloaded event for ${event.id} was not properly received")
              return@collect
            }
            channel.send(event.event)
          }
        }
      }
    }
    parentScope.awaitCancellationAndInvoke {
      preloadedEvents.clear()
    }
  }

  fun getChildrenEventsFlow(entityId: XContainerId): Flow<XValueComputeChildrenEvent>? {
    // channel can be consumed only once, so we remove it here
    val eventsChannel = preloadedEvents.remove(entityId) ?: return null
    return channelFlow {
      eventsChannel.consumeEach { send(it) }
    }
  }

  private fun markToBeLoaded(id: XContainerId, localEvents: MutableMap<XContainerId, Channel<XValueComputeChildrenEvent>>) {
    val channel = Channel<XValueComputeChildrenEvent>(capacity = Channel.UNLIMITED)
    val old = localEvents.put(id, channel)
    assert(old == null) { "Channel for $id was already registered" }
    preloadedEvents[id] = channel
  }

  companion object {
    internal fun creteIfNeeded(
      parentScope: CoroutineScope,
      treeState: XDebuggerTreeState?,
      xFrameId: XStackFrameId,
    ): VariablesPreloadManager? {
      val rootInfo = treeState?.rootInfo ?: return null
      val root = rootInfo.toRpc() ?: return null
      return VariablesPreloadManager(parentScope, root, xFrameId)
    }
  }
}

private fun XDebuggerTreeState.NodeInfo.toRpc(): XDebuggerTreeExpandedNode? {
  if (!isExpanded) return null
  val childrenList = children?.mapNotNull { it.toRpc() } ?: emptyList()
  return XDebuggerTreeExpandedNode(name, childrenList)
}
