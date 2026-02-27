// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.service.AgentSessionsService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

internal class AgentSessionsOpenDedicatedFrameAction : DumbAwareAction {
  private val isDedicatedProject: (Project) -> Boolean
  private val openDedicatedFrame: (Project) -> Unit

  @Suppress("unused")
  constructor() {
    isDedicatedProject = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject
    openDedicatedFrame = { project -> service<AgentSessionsService>().openOrFocusDedicatedFrame(project) }
  }

  internal constructor(
    isDedicatedProject: (Project) -> Boolean,
    openDedicatedFrame: (Project) -> Unit,
  ) {
    this.isDedicatedProject = isDedicatedProject
    this.openDedicatedFrame = openDedicatedFrame
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (isDedicatedProject(project)) {
      return
    }
    openDedicatedFrame(project)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && !isDedicatedProject(project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
