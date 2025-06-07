// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XSuspendContextId
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ConsistentCopyVisibility
@ApiStatus.Internal
data class XSuspendContextModel internal constructor(
  val coroutineScope: CoroutineScope,
  val suspendContext: XSuspendContext,
  val session: XDebugSessionImpl,
)

@ApiStatus.Internal
fun XSuspendContextId.findValue(): XSuspendContextModel? {
  return findValueById(this, type = XSuspendContextValueIdType)
}

@ApiStatus.Internal
fun XSuspendContext.getOrStoreGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XSuspendContextId {
  return with(XSuspendContextDeduplicator.getInstance()) {
    getOrStoreGlobally(coroutineScope, session)
  }
}

@Service(Service.Level.APP)
private class XSuspendContextDeduplicator {
  private val storage = ConcurrentHashMap<CoroutineScope, ScopeBoundStorage>()

  fun XSuspendContext.getOrStoreGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XSuspendContextId {
    return getOrCreateStorage(coroutineScope).getOrStore(this) {
      storeValueGlobally(coroutineScope, XSuspendContextModel(coroutineScope, this, session), type = XSuspendContextValueIdType)
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
    private val storage = ConcurrentHashMap<XSuspendContext, XSuspendContextId>()

    fun getOrStore(suspendContext: XSuspendContext, createId: () -> XSuspendContextId): XSuspendContextId {
      return storage.computeIfAbsent(suspendContext) { createId() }
    }

    fun clear() {
      storage.clear()
    }
  }

  companion object {
    fun getInstance(): XSuspendContextDeduplicator = service()
  }
}

private object XSuspendContextValueIdType : BackendValueIdType<XSuspendContextId, XSuspendContextModel>(::XSuspendContextId)