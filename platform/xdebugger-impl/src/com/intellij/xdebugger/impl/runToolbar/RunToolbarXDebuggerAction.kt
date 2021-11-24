// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.runToolbar

import com.intellij.execution.runToolbar.RTBarAction
import com.intellij.execution.runToolbar.RunToolbarMainSlotState
import com.intellij.execution.runToolbar.RunToolbarProcess
import com.intellij.execution.runToolbar.mainState
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase
import com.intellij.xdebugger.impl.actions.handlers.RunToolbarPauseActionHandler
import com.intellij.xdebugger.impl.actions.handlers.RunToolbarResumeActionHandler

abstract class RunToolbarXDebuggerAction : XDebuggerActionBase(false), RTBarAction {
  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.PROCESS
  }

  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.RIGHT_FLEXIBLE

  override fun update(e: AnActionEvent) {
    super.update(e)

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && checkMainSlotVisibility(it)
      }
    }
  }

  override fun setShortcutSet(shortcutSet: ShortcutSet) {}
}

open class RunToolbarPauseAction : RunToolbarXDebuggerAction() {
  private val handler = RunToolbarPauseActionHandler()

  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return handler
  }

  init {
    templatePresentation.icon = AllIcons.Actions.Pause
  }
}

open class RunToolbarResumeAction : RunToolbarXDebuggerAction() {
  private val handler = RunToolbarResumeActionHandler()

  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return handler
  }

  init {
    templatePresentation.icon = AllIcons.Actions.Resume
  }
}
