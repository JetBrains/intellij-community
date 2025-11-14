// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
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
  private val cs = parentScope.childScope("VariablesPreloadManager")
  private val preloadedEvents = ConcurrentHashMap<XContainerId, Channel<XValueComputeChildrenEvent>>()

  init {
    markToBeLoaded(frameId)
    cs.launch {
      XValueApi.getInstance().computeExpandedChildren(frameId, treeState).collect { event ->
        when (event) {
          is PreloadChildrenEvent.ToBePreloaded -> {
            markToBeLoaded(event.id)
          }
          is PreloadChildrenEvent.ExpandedChildrenEvent -> {
            val channel = preloadedEvents[event.id] ?: run {
              fileLogger().error("Preloaded event for ${event.id} was not properly received")
              return@collect
            }
            channel.send(event.event)
          }
        }
      }
    }
  }

  fun getChildrenEventsFlow(entityId: XContainerId): Flow<XValueComputeChildrenEvent>? {
    val eventsChannel = preloadedEvents[entityId] ?: return null
    return channelFlow {
      eventsChannel.consumeEach { send(it) }
    }
  }

  private fun markToBeLoaded(id: XContainerId) {
    val old = preloadedEvents.put(id, Channel(capacity = Channel.UNLIMITED))
    assert(old == null) { "Channel for $id was already registered" }
  }

  internal fun cancel() {
    cs.cancel()
  }

  companion object {
    internal fun creteIfNeeded(
      parentScope: CoroutineScope,
      treeState: XDebuggerTreeState?,
      xFrameId: XStackFrameId,
    ): VariablesPreloadManager? {
      val rootInfo = treeState?.rootInfo ?: return null
      if (!rootInfo.isExpanded) return null
      return VariablesPreloadManager(parentScope, rootInfo.toRpc(), xFrameId)
    }
  }
}

private fun XDebuggerTreeState.NodeInfo.toRpc(): XDebuggerTreeExpandedNode {
  if (!isExpanded) {
    return XDebuggerTreeExpandedNode(name, emptyList())
  }
  val childrenList = children?.map { it.toRpc() } ?: emptyList()
  return XDebuggerTreeExpandedNode(name, childrenList)
}
