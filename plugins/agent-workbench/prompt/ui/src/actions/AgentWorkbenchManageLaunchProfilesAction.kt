// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-task-cost-profiles.spec.md

import com.intellij.agent.workbench.prompt.ui.AgentPromptLaunchProfileManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

@Suppress("unused")
internal class AgentWorkbenchManageLaunchProfilesAction(
  private val openProfiles: (Project) -> Unit,
) : DumbAwareAction() {
  constructor() : this({ project -> AgentPromptLaunchProfileManager(project).openOrFocus() })

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
    e.presentation.keepPopupOnPerform = KeepPopupOnPerform.Never
  }

  override fun actionPerformed(e: AnActionEvent) {
    openProfiles(e.project ?: return)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
