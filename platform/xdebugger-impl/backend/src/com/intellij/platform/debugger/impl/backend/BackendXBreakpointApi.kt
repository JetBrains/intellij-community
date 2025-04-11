// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.edtWriteAction
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.rpc.XBreakpointApi
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeId
import com.intellij.xdebugger.impl.rpc.models.findValue

internal class BackendXBreakpointApi : XBreakpointApi {
  override suspend fun setEnabled(breakpointId: XBreakpointId, enabled: Boolean) {
    val breakpoint = breakpointId.findValue() ?: return
    edtWriteAction {
      breakpoint.isEnabled = enabled
    }
  }

  override suspend fun setSuspendPolicy(breakpointId: XBreakpointId, suspendPolicy: SuspendPolicy) {
    val breakpoint = breakpointId.findValue() ?: return
    edtWriteAction {
      breakpoint.suspendPolicy = suspendPolicy
    }
  }

  override suspend fun setDefaultSuspendPolicy(project: ProjectId, breakpointTypeId: XBreakpointTypeId, policy: SuspendPolicy) {
    val project = project.findProject()
    edtWriteAction {
      val type = XBreakpointUtil.findType(breakpointTypeId.id) ?: return@edtWriteAction
      (XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl).getBreakpointDefaults(type).suspendPolicy = policy
    }
  }
}