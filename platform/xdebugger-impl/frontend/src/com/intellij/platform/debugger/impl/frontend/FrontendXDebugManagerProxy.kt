// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XValueId
import kotlinx.coroutines.flow.Flow

private class FrontendXDebugManagerProxy : XDebugManagerProxy {
  override fun isEnabled(): Boolean = XDebugSessionProxy.useFeProxy()
  override suspend fun <T> withId(value: XValue, session: XDebugSessionProxy, block: suspend (XValueId) -> T): T {
    val valueId = (value as FrontendXValue).xValueDto.id
    return block(valueId)
  }

  override fun getCurrentSessionProxy(project: Project): XDebugSessionProxy? {
    return FrontendXDebuggerManager.getInstance(project).currentSession.value
  }

  override fun getSessionIdByContentDescriptor(project: Project, descriptor: RunContentDescriptor): XDebugSessionId? {
    return FrontendXDebuggerManager.getInstance(project).getSessionIdByContentDescriptor(descriptor)
  }

  override fun getCurrentSessionFlow(project: Project): Flow<XDebugSessionProxy?> {
    return FrontendXDebuggerManager.getInstance(project).currentSession
  }

  override fun getBreakpointManagerProxy(project: Project): XBreakpointManagerProxy {
    return FrontendXDebuggerManager.getInstance(project).breakpointsManager
  }
}
