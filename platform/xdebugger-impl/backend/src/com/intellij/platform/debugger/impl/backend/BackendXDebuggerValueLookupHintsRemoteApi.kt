// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.debugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.xdebugger.impl.evaluate.ValueLookupManagerController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal class BackendXDebuggerValueLookupHintsRemoteApi : XDebuggerValueLookupHintsRemoteApi {
  override suspend fun getValueLookupListeningFlow(projectId: ProjectId): Flow<Boolean> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return ValueLookupManagerController.getInstance(project).getListeningStateFlow()
  }
}
