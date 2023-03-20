package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.attach.XAttachDebugger
import com.intellij.xdebugger.impl.actions.AttachToProcessActionBase

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
