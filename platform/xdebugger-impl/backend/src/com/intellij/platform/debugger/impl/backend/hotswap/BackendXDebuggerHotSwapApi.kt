// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend.hotswap

import com.intellij.platform.debugger.impl.rpc.HotSwapSource
import com.intellij.platform.debugger.impl.rpc.XDebugHotSwapCurrentSessionStatus
import com.intellij.platform.debugger.impl.rpc.XDebugHotSwapSessionId
import com.intellij.platform.debugger.impl.rpc.XDebuggerHotSwapApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.xdebugger.impl.hotswap.HotSwapSessionManagerImpl
import com.intellij.xdebugger.impl.hotswap.HotSwapStatistics
import com.intellij.xdebugger.impl.hotswap.findHotSwapSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class BackendXDebuggerHotSwapApi : XDebuggerHotSwapApi {
  override suspend fun currentSessionStatus(projectId: ProjectId): Flow<XDebugHotSwapCurrentSessionStatus?> {
    val project = projectId.findProject()
    return HotSwapSessionManagerImpl.getInstance(project).currentStatusFlow.map { state ->
      if (state == null) return@map null
      val (session, status) = state
      XDebugHotSwapCurrentSessionStatus(session.id, status)
    }
  }

  override suspend fun performHotSwap(sessionId: XDebugHotSwapSessionId, source: HotSwapSource) {
    val session = sessionId.findHotSwapSession() ?: return
    HotSwapStatistics.logHotSwapCalled(session.project, source)
    session.performHotSwap()
  }

  override suspend fun restart(sessionId: XDebugHotSwapSessionId) {
    val session = sessionId.findHotSwapSession() ?: return
    session.restart()
  }

  override suspend fun hide(projectId: ProjectId) {
    val project = projectId.findProject()
    HotSwapSessionManagerImpl.getInstance(project).hide()
  }
}
