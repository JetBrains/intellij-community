// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.stateWidget

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase
import com.intellij.xdebugger.impl.actions.handlers.StateWidgetPauseActionHandler
import com.intellij.xdebugger.impl.actions.handlers.StateWidgetResumeActionHandler

abstract class StateWidgetXDebuggerAction : XDebuggerActionBase(true) {
  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabledAndVisible = event.presentation.isEnabled && event.presentation.isVisible
  }
}

class StateWidgetPauseAction : StateWidgetXDebuggerAction() {
  private val handler = StateWidgetPauseActionHandler()

  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return handler
  }
}

class StateWidgetResumeAction : StateWidgetXDebuggerAction() {
  private val handler = StateWidgetResumeActionHandler()

  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return handler
  }
}
