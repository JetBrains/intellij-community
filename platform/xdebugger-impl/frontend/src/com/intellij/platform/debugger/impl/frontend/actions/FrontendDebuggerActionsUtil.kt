// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.FrontendXDebuggerManager
import com.intellij.platform.debugger.impl.frontend.FrontendXDebuggerSession
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val AnActionEvent.frontendDebuggerSession: FrontendXDebuggerSession?
  get() {
    val project = project ?: return null
    return FrontendXDebuggerManager.getInstance(project).currentSession.value
  }

internal fun performDebuggerActionAsync(
  e: AnActionEvent,
  updateInlays: Boolean = false,
  action: suspend () -> Unit,
) {
  val project = e.project
  val coroutineScope = project?.service<FrontendDebuggerActionProjectCoroutineScope>()?.cs
                       ?: service<FrontendDebuggerActionCoroutineScope>().cs

  coroutineScope.launch {
    action()
    if (updateInlays) {
      val editor = e.getData(CommonDataKeys.EDITOR)
      if (project != null && editor != null) {
        withContext(Dispatchers.EDT) {
          XDebuggerManagerApi.getInstance().reshowInlays(project.projectId(), editor.editorId())
        }
      }
    }
  }
}

internal fun updateSuspendedAction(e: AnActionEvent) {
  val session = e.frontendDebuggerSession
  if (session == null) {
    e.presentation.isEnabled = false
    return
  }
  e.presentation.isEnabled = !session.isReadOnly && session.isSuspended
}

@Service(Service.Level.APP)
private class FrontendDebuggerActionCoroutineScope(val cs: CoroutineScope)

@Service(Service.Level.PROJECT)
private class FrontendDebuggerActionProjectCoroutineScope(val project: Project, val cs: CoroutineScope)