// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.xdebugger.impl.findValue
import com.intellij.xdebugger.impl.rpc.models.findValue
import fleet.rpc.core.toRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class BackendXDebugSessionTabApi : XDebugSessionTabApi {
  override suspend fun sessionTabInfo(sessionDataId: XDebugSessionDataId): Flow<XDebuggerSessionTabDto> {
    val session = sessionDataId.findValue()?.session ?: return emptyFlow()
    return session.tabInitDataFlow.map {
      XDebuggerSessionTabDto(it, session.getPausedEventsFlow().toRpc())
    }
  }

  override suspend fun onTabInitialized(sessionId: XDebugSessionId, tabInfo: XDebuggerSessionTabInfoCallback) {
    val session = sessionId.findValue() ?: return
    session.tabInitialized(tabInfo.tab)
  }

  override suspend fun additionalTabEvents(tabComponentsManagerId: XDebugSessionAdditionalTabComponentManagerId): Flow<XDebuggerSessionAdditionalTabEvent> {
    val manager = tabComponentsManagerId.findValue() ?: return emptyFlow()
    return manager.tabComponentEvents
  }

  override suspend fun tabLayouterEvents(tabLayouterId: XDebugTabLayouterId): Flow<XDebugTabLayouterEvent> {
    val layouterModel = tabLayouterId.findValue() ?: return emptyFlow()
    // TODO Support XDebugTabLayouter.registerConsoleContent
    withContext(Dispatchers.EDT) {
      layouterModel.layouter.registerAdditionalContent(layouterModel.ui)
    }
    return layouterModel.events
  }
}

