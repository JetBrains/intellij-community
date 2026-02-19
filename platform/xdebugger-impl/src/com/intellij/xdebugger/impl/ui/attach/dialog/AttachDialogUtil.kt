// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.attach.XAttachDebugger
import com.intellij.xdebugger.impl.actions.AttachToProcessActionBase
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun attachToProcessWithDebugger(debugger: XAttachDebugger, item: AttachToProcessActionBase.AttachToProcessItem, project: Project) {
  AttachToProcessActionBase.addToRecent(project, item)
  try {
    debugger.attachDebugSession(project, item.host, item.processInfo)
  }
  catch (e: ExecutionException) {
    val message = XDebuggerBundle.message("xdebugger.attach.pid", item.processInfo.pid)
    ExecutionUtil.handleExecutionError(project, ToolWindowId.DEBUG, message, e)
  }
}
