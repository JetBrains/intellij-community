// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.frame.CurrentXDebugSessionProxyProvider
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxyKeeper
import com.intellij.xdebugger.impl.rpc.XDebuggerEntityIdProvider
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModelsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private class MonolithCurrentSessionProxyProvider : CurrentXDebugSessionProxyProvider {
  override fun provideCurrentSessionProxy(project: Project): XDebugSessionProxy? {
    val session = XDebuggerManager.getInstance(project)?.currentSession ?: return null
    return XDebugSessionProxyKeeper.getInstance(project).getOrCreateProxy(session)
  }
}

private class MonolithXDebuggerEntityIdProvider : XDebuggerEntityIdProvider {
  override fun isEnabled(): Boolean = !XDebugSessionProxy.useFeProxy()
  override suspend fun <T> withId(value: XValue, session: XDebugSessionProxy, block: suspend (XValueId) -> T): T {
    val sessionImpl = (session as XDebugSessionProxy.Monolith).session as XDebugSessionImpl
    return withCoroutineScopeForId(block) { scope ->
      BackendXValueModelsManager.getInstance(session.project).createXValueModel(scope, sessionImpl, value).id
    }
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
