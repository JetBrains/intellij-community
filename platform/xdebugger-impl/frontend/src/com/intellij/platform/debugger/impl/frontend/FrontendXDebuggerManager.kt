// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.supervisorScope

@Service(Service.Level.PROJECT)
internal class FrontendXDebuggerManager(private val project: Project, private val cs: CoroutineScope) {
  @OptIn(ExperimentalCoroutinesApi::class)
  val currentSession: StateFlow<FrontendXDebuggerSession?> =
    channelFlow<FrontendXDebuggerSession?> {
      XDebuggerManagerApi.getInstance().currentSession(project.projectId()).collectLatest { sessionDto ->
        if (sessionDto == null) {
          send(null)
          return@collectLatest
        }
        supervisorScope {
          val session = FrontendXDebuggerSession(project, this, sessionDto)
          send(session)
          awaitCancellation()
        }
      }
    }.stateIn(cs, SharingStarted.Eagerly, null)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendXDebuggerManager = project.service()
  }
}