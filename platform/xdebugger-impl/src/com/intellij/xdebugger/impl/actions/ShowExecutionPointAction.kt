// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.performDebuggerActionAsync
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShowExecutionPointAction : XDebuggerActionBase(), SplitDebuggerAction {
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
      val executionStack = session.getCurrentExecutionStack() ?: return@performDebuggerActionAsync
      val topFrame = executionStack.topFrameAsync.await() ?: return@performDebuggerActionAsync
      session.setCurrentStackFrame(executionStack, topFrame, true)
    }
  }
}
