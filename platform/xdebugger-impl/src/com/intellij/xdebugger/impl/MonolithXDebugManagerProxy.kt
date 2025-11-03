// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.idea.AppMode
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import com.intellij.platform.debugger.impl.rpc.XValueId
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy
import com.intellij.xdebugger.impl.breakpoints.asProxy
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.frame.asProxy
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.models.getOrStoreGlobally
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus

private class MonolithXDebugManagerProxy : XDebugManagerProxy {
  override fun getCurrentSessionProxy(project: Project): XDebugSessionProxy? {
    val session = XDebuggerManager.getInstance(project)?.currentSession ?: return null
    return session.asProxy()
  }

  override fun isEnabled(): Boolean {
    return !SplitDebuggerMode.isSplitDebugger() || FrontendApplicationInfo.getFrontendType() is FrontendType.Monolith
  }

  override suspend fun <T> withId(value: XValue, session: XDebugSessionProxy, block: suspend (XValueId) -> T): T {
    val sessionImpl = (session as XDebugSessionProxy.Monolith).sessionImpl
    return withTemporaryXValueId(value, sessionImpl, block)
  }

  // This method is not supported in monolith mode
  override fun getXValueId(value: XValue): XValueId? = null

  // This method is not supported in monolith mode
  override fun getXExecutionStackId(stack: XExecutionStack): XExecutionStackId? = null

  override suspend fun <T> withId(stack: XExecutionStack, session: XDebugSessionProxy, block: suspend (XExecutionStackId) -> T): T {
    val sessionImpl = (session as XDebugSessionProxy.Monolith).sessionImpl
    return withCoroutineScopeForId(block) { scope ->
      val (_, id) = stack.getOrStoreGlobally(scope, sessionImpl)
      id
    }
  }

  override fun getCurrentSessionFlow(project: Project): Flow<XDebugSessionProxy?> {
    val managerImpl = XDebuggerManager.getInstance(project) as XDebuggerManagerImpl
    return managerImpl.currentSessionFlow.map { it?.asProxy() }
  }

  override fun getSessions(project: Project): List<XDebugSessionProxy> {
    val managerImpl = XDebuggerManager.getInstance(project) as XDebuggerManagerImpl
    return managerImpl.debugSessions.map { it.asProxy() }
  }

  override fun getBreakpointManagerProxy(project: Project): XBreakpointManagerProxy {
    val manager = XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl
    return manager.asProxy()
  }

  override fun getDebuggerExecutionPointManager(project: Project): XDebuggerExecutionPointManager? {
    if (AppMode.isRemoteDevHost() && SplitDebuggerMode.isSplitDebugger()) {
      return null
    }
    return XDebuggerExecutionPointManager.getInstance(project)
  }

  override fun hasBackendCounterpart(xValue: XValue): Boolean {
    return true
  }
}

@ApiStatus.Internal
suspend fun <T> withTemporaryXValueId(
  value: XValue,
  sessionImpl: XDebugSessionImpl,
  block: suspend (XValueId) -> T,
): T = withCoroutineScopeForId(block) { scope ->
  BackendXValueModel(scope, sessionImpl, value, precomputePresentation = false).id
}

private suspend fun <ID, T> withCoroutineScopeForId(block: suspend (ID) -> T, idProvider: (CoroutineScope) -> ID): T {
  return coroutineScope {
    val channel = Channel<Deferred<T>>()
    val job = launch {
      // This scope should be canceled, as idProvider can use awaitCancellation
      val resultCalculation = async {
        val id = idProvider(this@launch)
        block(id)
      }
      channel.send(resultCalculation)
    }

    val deferred = channel.receive()
    try {
      deferred.await()
    }
    finally {
      job.cancel()
    }
  }
}
