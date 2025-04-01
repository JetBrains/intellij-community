// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.rpc.XDebuggerEntityIdProvider
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModelsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.supervisorScope

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
  var result: T? = null
  supervisorScope {
    val id = idProvider(this)
    result = block(id)
    cancel()
  }
  @Suppress("UNCHECKED_CAST")
  return result as T
}
