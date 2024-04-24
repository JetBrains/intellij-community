// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.runToolbar

import com.intellij.execution.ExecutorActionStatus
import com.intellij.execution.InlineResumeCreator
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.runToolbar.*
import com.intellij.execution.ui.RunWidgetResumeManager
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemotePermissionRequirements
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase
import com.intellij.xdebugger.impl.actions.handlers.*

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

class InlineXDebuggerResumeAction(configurationSettings: RunnerAndConfigurationSettings) : XDebuggerResumeAction() {
  private val inlineHandler = InlineXDebuggerResumeHandler(configurationSettings)
  override fun getResumeHandler(): InlineXDebuggerResumeHandler {
    return inlineHandler
  }
}

class CurrentSessionXDebuggerResumeAction : XDebuggerResumeAction() {
  private val currentSessionHandler = CurrentSessionXDebuggerResumeHandler()
  override fun getResumeHandler(): CurrentSessionXDebuggerResumeHandler {
    return currentSessionHandler
  }

  override fun update(event: AnActionEvent) {
    event.project?.let {
      if(!RunWidgetResumeManager.getInstance(it).isFirstVersionAvailable()) {
        event.presentation.isEnabledAndVisible = false
        return
      }
    }
    super.update(event)
  }
}

open class ConfigurationXDebuggerResumeAction : XDebuggerResumeAction() {
  private val handler = XDebuggerResumeHandler()
  override fun getResumeHandler(): XDebuggerResumeHandler {
    return handler
  }

  override fun update(event: AnActionEvent) {
    event.project?.let {
      if(!RunWidgetResumeManager.getInstance(it).isSecondVersionAvailable()) {
        event.presentation.isEnabledAndVisible = false
        return
      }
    }
    super.update(event)
  }
}


abstract class XDebuggerResumeAction : XDebuggerActionBase(false),
                                       ActionRemotePermissionRequirements.RunAccess {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return getResumeHandler()
  }

  abstract fun getResumeHandler() : CurrentSessionXDebuggerResumeHandler

  override fun update(event: AnActionEvent) {
    super.update(event)

    val resumeHandler = getResumeHandler()
    val state = resumeHandler.getState(event)
    val conf = event.project?.let {
      if (resumeHandler is XDebuggerResumeHandler) {
        resumeHandler.getConfiguration(it)?.configuration?.name
      } else null
    }

    updatePresentation(event.presentation, state, conf)
    event.presentation.isEnabled = state != null

    event.presentation.putClientProperty(ExecutorActionStatus.KEY, ExecutorActionStatus.NORMAL)
  }

  init {
    updatePresentation(templatePresentation, CurrentSessionXDebuggerResumeHandler.State.PAUSE)
  }

  private fun updatePresentation(presentation: Presentation, state: CurrentSessionXDebuggerResumeHandler.State?, config: @NlsSafe String? = null ) {
    when(state) {
      CurrentSessionXDebuggerResumeHandler.State.RESUME -> {
        presentation.icon = AllIcons.Actions.Resume
        presentation.text = config?.let{IdeBundle.message("description.text.resume", it)} ?: IdeBundle.message("comment.text.resume")
      }
      else -> {
        presentation.icon = AllIcons.Actions.Pause
        presentation.text = config?.let{IdeBundle.message("description.text.pause", it)} ?: IdeBundle.message("comment.text.pause")
      }
    }
  }
}

class XDebuggerInlineResumeCreator : InlineResumeCreator {
  override fun getInlineResumeCreator(settings: RunnerAndConfigurationSettings, isWidget: Boolean): AnAction {
    return InlineXDebuggerResumeAction(settings)
  }
}

