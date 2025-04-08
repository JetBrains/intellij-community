// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.DebuggerSupport

open class StepOutAction : XDebuggerActionBase() {
  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return ourHandler
  }
}

private val ourHandler: XDebuggerSuspendedActionHandler = object : XDebuggerSuspendedActionHandler() {
  override fun perform(session: XDebugSession, dataContext: DataContext) {
    session.stepOut()
  }
}
