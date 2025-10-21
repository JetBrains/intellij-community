// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.platform.debugger.impl.backend.hotswap

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.xdebugger.impl.hotswap.HotSwapSessionImpl
import com.intellij.xdebugger.impl.hotswap.HotSwapSessionManagerImpl
import com.intellij.xdebugger.impl.hotswap.HotSwapStatistics
import com.intellij.xdebugger.impl.rpc.HotSwapSource
import com.intellij.xdebugger.impl.rpc.XDebugHotSwapCurrentSessionStatus
import com.intellij.xdebugger.impl.rpc.XDebugHotSwapSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerHotSwapApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

internal class BackendXDebuggerHotSwapApi : XDebuggerHotSwapApi {
  override suspend fun currentSessionStatus(projectId: ProjectId): Flow<XDebugHotSwapCurrentSessionStatus?> {
    val project = projectId.findProject()
    return channelFlow {
      HotSwapSessionManagerImpl.getInstance(project).currentStatusFlow.collectLatest {
        if (it == null) {
          send(null)
          return@collectLatest
        }
        val session = it.session
        val status = it.status
        coroutineScope {
          val id = storeValueGlobally(this, session, type = HowSwapSessionValueIdType)
          send(XDebugHotSwapCurrentSessionStatus(id, status))
          awaitCancellation()
        }
      }
    }
  }

  override suspend fun performHotSwap(sessionId: XDebugHotSwapSessionId, source: HotSwapSource) {
    val session = findValueById(sessionId, type = HowSwapSessionValueIdType) ?: return
    HotSwapStatistics.logHotSwapCalled(session.project, source)
    session.performHotSwap()
  }

  override suspend fun hide(projectId: ProjectId) {
    val project = projectId.findProject()
    HotSwapSessionManagerImpl.getInstance(project).hide()
  }
}

private object HowSwapSessionValueIdType : BackendValueIdType<XDebugHotSwapSessionId, HotSwapSessionImpl<*>>(::XDebugHotSwapSessionId)