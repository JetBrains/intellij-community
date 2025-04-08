// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerRunToCursorActionHandler

open class ForceRunToCursorAction : XDebuggerActionBase(true) {
  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return ourHandler
  }
}

private val ourHandler = XDebuggerRunToCursorActionHandler(true)
