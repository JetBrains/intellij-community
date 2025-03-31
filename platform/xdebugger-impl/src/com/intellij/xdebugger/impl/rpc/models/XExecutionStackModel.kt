// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XExecutionStackId
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

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

@ApiStatus.Internal
fun XExecutionStack.getOrStoreGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XExecutionStackId {
  return with(XExecutionStackDeduplicator.getInstance()) {
    getOrStoreGlobally(coroutineScope, session)
  }
}

@Service(Service.Level.APP)
private class XExecutionStackDeduplicator {
  private val storage = ConcurrentHashMap<CoroutineScope, ScopeBoundStorage>()

  fun XExecutionStack.getOrStoreGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XExecutionStackId {
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
    private val storage = ConcurrentHashMap<XExecutionStack, XExecutionStackId>()

    fun getOrStore(stack: XExecutionStack, createId: () -> XExecutionStackId): XExecutionStackId {
      return storage.computeIfAbsent(stack) { createId() }
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