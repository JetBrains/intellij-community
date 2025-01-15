// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.kernel.backend.withNullableIDsFlow
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.rpc.XDebugSessionDto
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import kotlinx.coroutines.flow.Flow

internal class BackendXDebuggerManagerApi : XDebuggerManagerApi {
  override suspend fun currentSession(projectId: ProjectId): Flow<XDebugSessionDto?> {
    val project = projectId.findProject()

    return (XDebuggerManager.getInstance(project) as XDebuggerManagerImpl).currentSessionFlow.withNullableIDsFlow { id, currentSession ->
      if (id == null) {
        return@withNullableIDsFlow null
      }
      XDebugSessionDto(XDebugSessionId(id), currentSession?.debugProcess?.editorsProvider)
    }
  }
}