// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.agent.workbench.sessions.service.openableSourceProjectPath
import com.intellij.agent.workbench.sessions.settings.AgentThreadsProjectScopeSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project

internal class AgentSessionsCurrentProjectOnlyToggleAction @JvmOverloads constructor(
  private val currentProjectPath: (Project) -> String? = ::openableSourceProjectPath,
  private val isCurrentProjectOnly: () -> Boolean = AgentThreadsProjectScopeSettings::isCurrentProjectOnly,
  private val setCurrentProjectOnly: (Boolean) -> Unit = AgentThreadsProjectScopeSettings::setCurrentProjectOnly,
) : DumbAwareToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return currentProjectPath(project) != null && isCurrentProjectOnly()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    if (currentProjectPath(project) == null) return
    setCurrentProjectOnly(state)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    e.presentation.isEnabled = project != null && currentProjectPath(project) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
