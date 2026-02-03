// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XDebuggerManagerApi
import com.intellij.platform.project.projectId
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun performDebuggerActionAsync(e: AnActionEvent, action: suspend () -> Unit) {
  performDebuggerActionAsync(e.project, e.dataContext, action)
}

@ApiStatus.Internal
fun performDebuggerActionAsync(
  project: Project?,
  dataContext: DataContext,
  action: suspend () -> Unit,
) {
  getFrontendDebuggerActionCoroutineScope(project).launch {
    action()
    reshowInlays(project, dataContext)
  }
}

@ApiStatus.Internal
fun performDebuggerAction(
  project: Project?,
  dataContext: DataContext,
  action: () -> Unit,
) {
  EDT.assertIsEdt()
  action()
  getFrontendDebuggerActionCoroutineScope(project).launch {
    reshowInlays(project, dataContext)
  }
}

private fun getFrontendDebuggerActionCoroutineScope(project: Project?): CoroutineScope =
  project?.service<FrontendDebuggerActionProjectCoroutineScope>()?.cs
         ?: service<FrontendDebuggerActionCoroutineScope>().cs

private suspend fun reshowInlays(project: Project?, dataContext: DataContext) {
  val editor = dataContext.getData(CommonDataKeys.EDITOR)
  if (project != null && editor != null) {
    XDebuggerManagerApi.getInstance().reshowInlays(project.projectId(), editor.editorId())
  }
}

@Service(Service.Level.APP)
private class FrontendDebuggerActionCoroutineScope(val cs: CoroutineScope)

@Service(Service.Level.PROJECT)
private class FrontendDebuggerActionProjectCoroutineScope(val cs: CoroutineScope)
