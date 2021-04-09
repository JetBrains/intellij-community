// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.stateWidget

import com.intellij.execution.segmentedRunDebugWidget.RunToolbarAction
import com.intellij.execution.segmentedRunDebugWidget.RunToolbarSlotManager
import com.intellij.execution.segmentedRunDebugWidget.isItRunToolbarMainSlot
import com.intellij.execution.segmentedRunDebugWidget.isOpened
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase
import com.intellij.xdebugger.impl.actions.handlers.RunToolbarPauseActionHandler
import com.intellij.xdebugger.impl.actions.handlers.RunToolbarResumeActionHandler

abstract class RunToolbarXDebuggerAction : XDebuggerActionBase(true) {
  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabledAndVisible = event.presentation.isEnabled && event.presentation.isVisible && if(event.isItRunToolbarMainSlot() && !event.isOpened()) event.project?.let {
      RunToolbarSlotManager.getInstance(it).getState().isSingleMain()
    } ?: false else true
  }
}

class RunToolbarPauseAction : RunToolbarXDebuggerAction(), RunToolbarAction {
  private val handler = RunToolbarPauseActionHandler()

  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return handler
  }

  init {
    templatePresentation.icon = AllIcons.Actions.Pause
  }
}

class RunToolbarResumeAction : RunToolbarXDebuggerAction(), RunToolbarAction {
  private val handler = RunToolbarResumeActionHandler()

  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return handler
  }

  init {
    templatePresentation.icon = AllIcons.Actions.Resume
  }
}
