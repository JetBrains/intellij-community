// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.kernel.backend.delete
import com.intellij.platform.kernel.backend.registerValueEntity
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

internal class BackendXDebuggerManagerApi : XDebuggerManagerApi {
  override suspend fun currentSession(projectId: ProjectId): Flow<XDebugSessionId?> {
    val project = projectId.findProject()

    return channelFlow {
      (XDebuggerManager.getInstance(project) as XDebuggerManagerImpl).currentSessionFlow.collectLatest { currentSession ->
        if (currentSession == null) {
          send(null)
          return@collectLatest
        }
        coroutineScope {
          val sessionEntity = registerValueEntity(currentSession)
          send(XDebugSessionId(sessionEntity.id))
          try {
            awaitCancellation()
          }
          catch (_: CancellationException) {
            sessionEntity.delete()
          }
        }
      }
    }
  }
}