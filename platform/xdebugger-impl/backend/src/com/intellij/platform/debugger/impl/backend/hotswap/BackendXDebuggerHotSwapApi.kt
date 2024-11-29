// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.platform.debugger.impl.backend.hotswap

import com.intellij.platform.kernel.backend.asNullableIDsFlow
import com.intellij.platform.kernel.backend.findValueEntity
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.xdebugger.impl.hotswap.HotSwapSession
import com.intellij.xdebugger.impl.hotswap.HotSwapSessionManager
import com.intellij.xdebugger.impl.hotswap.HotSwapStatistics
import com.intellij.xdebugger.impl.rpc.XDebugHotSwapCurrentSessionStatus
import com.intellij.xdebugger.impl.rpc.XDebugHotSwapSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerHotSwapApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

internal class BackendXDebuggerHotSwapApi : XDebuggerHotSwapApi {
  override suspend fun currentSessionStatus(projectId: ProjectId): Flow<XDebugHotSwapCurrentSessionStatus?> {
    val project = projectId.findProject()
    val flow = HotSwapSessionManager.getInstance(project).currentStatusFlow
    val sessionFlow = flow.mapLatest { it?.session }
    val statusFlow = flow.mapLatest { it?.status }
    return sessionFlow.asNullableIDsFlow().combine(statusFlow) { uid, status ->
      if (uid == null || status == null) return@combine null
      XDebugHotSwapCurrentSessionStatus(XDebugHotSwapSessionId(uid), status)
    }
  }

  override suspend fun performHotSwap(sessionId: XDebugHotSwapSessionId, source: HotSwapStatistics.HotSwapSource) {
    val session = sessionId.eid.findValueEntity<HotSwapSession<*>>()?.value ?: return
    HotSwapStatistics.logHotSwapCalled(session.project, source)

    fun <T> doHotSwap(session: HotSwapSession<T>) = session.provider.performHotSwap(session)

    doHotSwap(session)
  }

  override suspend fun hide(projectId: ProjectId) {
    val project = projectId.findProject()
    HotSwapSessionManager.getInstance(project).hide()
  }
}
