// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.frame.asProxy
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private class MonolithXDebugManagerProxy : XDebugManagerProxy {
  override fun getCurrentSessionProxy(project: Project): XDebugSessionProxy? {
    val session = XDebuggerManager.getInstance(project)?.currentSession ?: return null
    return session.asProxy()
  }

  override fun isEnabled(): Boolean {
    return !XDebugSessionProxy.useFeProxy() || FrontendApplicationInfo.getFrontendType() is FrontendType.Monolith
  }

  override suspend fun <T> withId(value: XValue, session: XDebugSessionProxy, block: suspend (XValueId) -> T): T {
    val sessionImpl = (session as XDebugSessionProxy.Monolith).session as XDebugSessionImpl
    return withCoroutineScopeForId(block) { scope ->
      BackendXValueModel(scope, sessionImpl, value, precomputePresentation = false).id
    }
  }

  override fun getSessionIdByContentDescriptor(project: Project, descriptor: RunContentDescriptor): XDebugSessionId? {
    val sessions = XDebuggerManagerImpl.getInstance(project).debugSessions
    val session = sessions.firstOrNull { it.runContentDescriptor === descriptor } ?: return null
    return (session as XDebugSessionImpl).id
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
    return XBreakpointManagerProxy.Monolith(XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl)
  }

  override fun canUpdateInlineDebuggerFrames(): Boolean {
    return true
  }
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
