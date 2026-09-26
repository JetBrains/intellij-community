// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerRunToCursorActionHandler

open class RunToCursorAction : XDebuggerActionBase(true), SplitDebuggerAction {
  override fun getHandler(): DebuggerActionHandler {
    return ourHandler
  }

  override fun actionPerformed(e: AnActionEvent) {
    // Avoid additional `performDebuggerAction` call
    performWithHandler(e)
  }

}

private val ourHandler = XDebuggerRunToCursorActionHandler(false)
