// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.platform.debugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.performDebuggerActionAsync

open class ForceStepOverAction : XDebuggerActionBase(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
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
      XDebugSessionApi.getInstance().stepOver(session.id, ignoreBreakpoints = true)
    }
  }
}