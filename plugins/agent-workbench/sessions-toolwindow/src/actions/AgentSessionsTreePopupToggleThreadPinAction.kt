// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.agent.workbench.thread.view.AgentThreadViewOpenTabsPresentationStateService
import com.intellij.agent.workbench.thread.view.setAgentThreadViewEditorTabPinned
import com.intellij.agent.workbench.thread.view.setOpenTopLevelAgentThreadViewThreadTabsPinned
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ai.agent.sessions.core.SessionActionTarget
import kotlinx.coroutines.launch

internal class AgentSessionsTreePopupToggleThreadPinAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val isThreadPinned: (SessionActionTarget.Thread) -> Boolean
  private val openThread: (
    AgentSessionsTreePopupActionContext,
    SessionActionTarget.Thread,
    suspend (Project, VirtualFile) -> Unit,
  ) -> Unit
  private val setOpenTabsPinned: suspend (SessionActionTarget.Thread, Boolean) -> Int
  private val setOpenedFilePinned: suspend (Project, VirtualFile, Boolean) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    isThreadPinned = { target ->
      service<AgentThreadViewOpenTabsPresentationStateService>().state.value.isPinnedTopLevelThread(
        provider = target.provider,
        projectPath = target.path,
        threadId = target.threadId,
      )
    }
    openThread = openThread@{ context, target, openedThreadViewHandler ->
      val thread = target.thread ?: return@openThread
      service<AgentSessionLaunchService>().openThreadViewThread(
        path = target.path,
        thread = thread,
        entryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
        currentProject = context.project,
        openedThreadViewHandler = openedThreadViewHandler,
      )
    }
    setOpenTabsPinned = ::setOpenTabsPinnedForTarget
    setOpenedFilePinned = ::setAgentThreadViewEditorTabPinned
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    isThreadPinned: (SessionActionTarget.Thread) -> Boolean,
    openThread: (
      AgentSessionsTreePopupActionContext,
      SessionActionTarget.Thread,
      suspend (Project, VirtualFile) -> Unit,
    ) -> Unit,
    setOpenTabsPinned: suspend (SessionActionTarget.Thread, Boolean) -> Int,
    setOpenedFilePinned: suspend (Project, VirtualFile, Boolean) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.isThreadPinned = isThreadPinned
    this.openThread = openThread
    this.setOpenTabsPinned = setOpenTabsPinned
    this.setOpenedFilePinned = setOpenedFilePinned
  }

  override fun update(e: AnActionEvent) {
    val target = resolveActiveThreadTarget(e)
    if (target == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val pinned = isThreadPinned(target)
    e.presentation.isEnabledAndVisible = true
    e.presentation.text = AgentSessionsBundle.message(
      if (pinned) "action.AgentWorkbenchSessions.TreePopup.TogglePin.unpin.text"
      else "action.AgentWorkbenchSessions.TreePopup.TogglePin.text"
    )
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val target = activeThreadTarget(context) ?: return
    if (isThreadPinned(target)) {
      e.coroutineScope.launch {
        setOpenTabsPinned(target, false)
      }
    }
    else {
      openThread(context, target) { project, file ->
        setOpenedFilePinned(project, file, true)
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun resolveActiveThreadTarget(e: AnActionEvent): SessionActionTarget.Thread? {
    return activeThreadTarget(resolveContext(e) ?: return null)
  }

  private fun activeThreadTarget(context: AgentSessionsTreePopupActionContext): SessionActionTarget.Thread? {
    val target = context.target as? SessionActionTarget.Thread ?: return null
    val thread = target.thread ?: return null
    if (thread.archived || isAgentSessionNewSessionId(target.threadId)) {
      return null
    }
    return target
  }
}

private suspend fun setOpenTabsPinnedForTarget(target: SessionActionTarget.Thread, pinned: Boolean): Int {
  return setOpenTopLevelAgentThreadViewThreadTabsPinned(
    provider = target.provider,
    projectPath = target.path,
    threadId = target.threadId,
    pinned = pinned,
  )
}
