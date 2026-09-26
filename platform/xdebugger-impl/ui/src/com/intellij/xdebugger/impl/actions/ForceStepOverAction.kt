// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction
import com.intellij.platform.debugger.impl.shared.performDebuggerActionAsync
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy

open class ForceStepOverAction : XDebuggerActionBase(), SplitDebuggerAction {
  override fun getHandler(): DebuggerActionHandler {
    return ourHandler
  }

  override fun actionPerformed(e: AnActionEvent) {
    // Avoid additional `performDebuggerAction` call
    performWithHandler(e)
  }
}

private val ourHandler = object : XDebuggerProxySuspendedActionHandler() {
  override fun perform(session: XDebugSessionProxy, event: AnActionEvent) {
    performDebuggerActionAsync(event) {
      session.stepOver(ignoreBreakpoints = true)
    }
  }
}
