// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ConsistentCopyVisibility
@ApiStatus.Internal
data class XStackFrameModel internal constructor(
  val coroutineScope: CoroutineScope,
  val stackFrame: XStackFrame,
  val session: XDebugSessionImpl,
)

@ApiStatus.Internal
fun XStackFrameId.findValue(): XStackFrameModel? {
  return findValueById(this, type = XStackFrameValueIdType)
}

@ApiStatus.Internal
fun XStackFrame.getOrStoreGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XStackFrameId {
  return with(XStackFrameDeduplicator.getInstance()) {
    getOrStoreGlobally(coroutineScope, session)
  }
}

@Service(Service.Level.APP)
private class XStackFrameDeduplicator {
  private val storage = ConcurrentHashMap<CoroutineScope, ScopeBoundStorage>()

  fun XStackFrame.getOrStoreGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XStackFrameId {
    return getOrCreateStorage(coroutineScope).getOrStore(this) {
      storeValueGlobally(coroutineScope, XStackFrameModel(coroutineScope, this, session), type = XStackFrameValueIdType)
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
    private val storage = ConcurrentHashMap<IdentityWrapper, XStackFrameId>()

    fun getOrStore(stack: XStackFrame, createId: () -> XStackFrameId): XStackFrameId {
      return storage.computeIfAbsent(IdentityWrapper(stack)) { createId() }
    }

    fun clear() {
      storage.clear()
    }
  }

  private class IdentityWrapper(val frame: XStackFrame) {
    override fun equals(other: Any?): Boolean {
      return other is IdentityWrapper && frame === other.frame
    }

    override fun hashCode(): Int {
      return System.identityHashCode(frame)
    }
  }

  companion object {
    fun getInstance(): XStackFrameDeduplicator = service()
  }
}

private object XStackFrameValueIdType : BackendValueIdType<XStackFrameId, XStackFrameModel>(::XStackFrameId)
