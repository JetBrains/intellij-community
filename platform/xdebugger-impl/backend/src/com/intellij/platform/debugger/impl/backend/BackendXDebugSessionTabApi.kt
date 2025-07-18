// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionAdditionalTabEvent
import com.intellij.xdebugger.impl.findValue
import com.intellij.xdebugger.impl.rpc.XDebugSessionAdditionalTabComponentManagerId
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebugSessionTabApi
import com.intellij.xdebugger.impl.rpc.XDebuggerSessionTabDto
import com.intellij.xdebugger.impl.rpc.XDebuggerSessionTabInfoCallback
import com.intellij.xdebugger.impl.rpc.models.findValue
import fleet.rpc.core.toRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class BackendXDebugSessionTabApi : XDebugSessionTabApi {
  override suspend fun sessionTabInfo(sessionId: XDebugSessionId): Flow<XDebuggerSessionTabDto?> {
    val session = sessionId.findValue() ?: return emptyFlow()
    return session.tabInitDataFlow.map {
      if (it == null) return@map null
      XDebuggerSessionTabDto(it, session.getPausedEventsFlow().toRpc())
    }
  }

  override suspend fun onTabInitialized(sessionId: XDebugSessionId, tabInfo: XDebuggerSessionTabInfoCallback) {
    val tab = tabInfo.tab ?: return
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.tabInitialized(tab)
    }
  }

  override suspend fun additionalTabEvents(tabComponentsManagerId: XDebugSessionAdditionalTabComponentManagerId): Flow<XDebuggerSessionAdditionalTabEvent> {
    val manager = tabComponentsManagerId.findValue() ?: return emptyFlow()
    return manager.tabComponentEvents
  }
}
