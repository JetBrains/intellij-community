// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XDebuggerManagerApi
import com.intellij.platform.project.projectId
import com.intellij.util.ui.EDT
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun performDebuggerActionAsync(e: AnActionEvent, action: suspend () -> Unit) {
  e.coroutineScope.launch {
    action()
    reshowInlays(e.project, e.dataContext)
  }
}

@ApiStatus.Internal
fun performDebuggerAction(e: AnActionEvent, action: () -> Unit) {
  EDT.assertIsEdt()
  action()
  e.coroutineScope.launch {
    reshowInlays(e.project, e.dataContext)
  }
}

private suspend fun reshowInlays(project: Project?, dataContext: DataContext) {
  val editor = dataContext.getData(CommonDataKeys.EDITOR)
  if (project != null && editor != null) {
    XDebuggerManagerApi.getInstance().reshowInlays(project.projectId(), editor.editorId())
  }
}
