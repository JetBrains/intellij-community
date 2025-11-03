// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXExecutionStack
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import com.intellij.platform.debugger.impl.rpc.XValueId
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebuggerExecutionPointManager
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.util.XDebugMonolithUtils
import com.intellij.xdebugger.impl.withTemporaryXValueId
import kotlinx.coroutines.flow.Flow

private class FrontendXDebugManagerProxy : XDebugManagerProxy {
  override fun isEnabled(): Boolean {
    val frontendType = FrontendApplicationInfo.getFrontendType()
    return SplitDebuggerMode.isSplitDebugger() ||
           (frontendType is FrontendType.Remote && frontendType.isGuest()) // CWM case
  }

  override fun hasBackendCounterpart(xValue: XValue): Boolean {
    return FrontendXValue.asFrontendXValueOrNull(xValue) != null
           || FrontendApplicationInfo.getFrontendType() is FrontendType.Monolith
  }

  override suspend fun <T> withId(value: XValue, session: XDebugSessionProxy, block: suspend (XValueId) -> T): T {
    val frontendXValue = FrontendXValue.asFrontendXValueOrNull(value)
    if (frontendXValue != null) {
      return block(frontendXValue.xValueDto.id)
    }
    else {
      // Otherwise try to fall back to monolith implementation if possible
      val monolithSession = XDebugMonolithUtils.findSessionById(session.id) ?: error("XValue is not a FrontendXValue: $value")
      return withTemporaryXValueId(value, monolithSession, block)
    }
  }

  override fun getXValueId(value: XValue): XValueId? =
    FrontendXValue.asFrontendXValueOrNull(value)?.xValueDto?.id

  override fun getXExecutionStackId(stack: XExecutionStack): XExecutionStackId? =
    (stack as? FrontendXExecutionStack)?.id

  override suspend fun <T> withId(stack: XExecutionStack, session: XDebugSessionProxy, block: suspend (XExecutionStackId) -> T): T {
    val executionStackId = (stack as FrontendXExecutionStack).id
    return block(executionStackId)
  }

  override fun getCurrentSessionProxy(project: Project): XDebugSessionProxy? {
    return FrontendXDebuggerManager.getInstance(project).currentSession
  }

  override fun getCurrentSessionFlow(project: Project): Flow<XDebugSessionProxy?> {
    return FrontendXDebuggerManager.getInstance(project).currentSessionFlow
  }

  override fun getSessions(project: Project): List<XDebugSessionProxy> {
    return FrontendXDebuggerManager.getInstance(project).sessions
  }

  override fun getBreakpointManagerProxy(project: Project): XBreakpointManagerProxy {
    return FrontendXDebuggerManager.getInstance(project).breakpointsManager
  }

  override fun getDebuggerExecutionPointManager(project: Project): XDebuggerExecutionPointManager? {
    return XDebuggerExecutionPointManager.getInstance(project)
  }
}
