// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAware
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.DebuggerSupport

// This action should be migrated to FrontendStepOverAction when debugger toolwindow won't be LUXed in Remote Dev
@Deprecated("Don't inherit from the action, implement your own")
open class StepOverAction : XDebuggerActionBase(), DumbAware {
  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return ourHandler
  }

  companion object {
    private val ourHandler: DebuggerActionHandler = object : XDebuggerSuspendedActionHandler() {
      override fun perform(session: XDebugSession, dataContext: DataContext) {
        session.stepOver(false)
      }
    }
  }
}
