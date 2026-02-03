// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.impl.XDebugSessionImpl
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ConsistentCopyVisibility
@ApiStatus.Internal
data class XExecutionStackModel internal constructor(
  val coroutineScope: CoroutineScope,
  val executionStack: XExecutionStack,
  val session: XDebugSessionImpl,
)

@ApiStatus.Internal
fun XExecutionStackId.findValue(): XExecutionStackModel? {
  return findValueById(this, type = XExecutionStackValueIdType)
}

private typealias StackAndId = Pair<XExecutionStack, XExecutionStackId>

@ApiStatus.Internal
fun XExecutionStack.getOrStoreGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): StackAndId {
  return with(XExecutionStackDeduplicator.getInstance()) {
    getOrStoreGlobally(coroutineScope, session)
  }
}

@Service(Service.Level.APP)
private class XExecutionStackDeduplicator {
  private val storage = ConcurrentHashMap<CoroutineScope, ScopeBoundStorage>()

  fun XExecutionStack.getOrStoreGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): StackAndId {
    return getOrCreateStorage(coroutineScope).getOrStore(this) {
      storeValueGlobally(coroutineScope, XExecutionStackModel(coroutineScope, this, session), type = XExecutionStackValueIdType)
    }
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  private fun getOrCreateStorage(coroutineScope: CoroutineScope) = storage.computeIfAbsent(coroutineScope) {
    ScopeBoundStorage().apply {
      coroutineScope.awaitCancellationAndInvoke {
        // Note that it's still up to BackendGlobalIdsManager to remove global IDs once their relevant coroutine scopes are cancelled
        storage.remove(coroutineScope)
        clear()
      }
    }
  }

  private class ScopeBoundStorage() {
    private val storage = ConcurrentHashMap<XExecutionStack, StackAndId>()

    fun getOrStore(stack: XExecutionStack, createId: () -> XExecutionStackId): StackAndId {
      return storage.computeIfAbsent(stack) { it to createId() }
    }

    fun clear() {
      storage.clear()
    }
  }

  companion object {
    fun getInstance(): XExecutionStackDeduplicator = service()
  }
}

private object XExecutionStackValueIdType : BackendValueIdType<XExecutionStackId, XExecutionStackModel>(::XExecutionStackId)