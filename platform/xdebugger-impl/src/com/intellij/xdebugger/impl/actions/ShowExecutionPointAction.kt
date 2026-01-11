// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.performDebuggerActionAsync
import com.intellij.xdebugger.impl.updateExecutionPosition
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShowExecutionPointAction : XDebuggerActionBase(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return ourHandler
  }

  override fun actionPerformed(e: AnActionEvent) {
    // Avoid additional `performDebuggerAction` call
    performWithHandler(e)
  }
}

private val ourHandler = object : XDebuggerProxySuspendedActionHandler() {
  override fun perform(session: XDebugSessionProxy, dataContext: DataContext) {
    performDebuggerActionAsync(session.project, dataContext) {
      session.switchToTopFrame()
      // TODO that's a temporary solution to make the action working in 253.
      //  This method shouldn't be called anywhere but `ExecutionPointManagerChangeListener`.
      //  Instead, here we should make sure `switchToTopFrame` actually makes the backend
      //  send a `FRAME_CHANGED` event back to the frontend,
      //  and the frontend should make an exception for this particular case
      //  and run the `stackFrameChanged` even if the stack frame wasn't changed.
      //  See IJPL-214299 for details.
      updateExecutionPosition(session)
    }
  }
}
