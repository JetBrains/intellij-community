// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class AgentChatNextProposedPlanAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    navigateSelectedAgentChatProposedPlan(project, AgentChatSemanticNavigationDirection.NEXT)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null &&
                                       canNavigateSelectedAgentChatProposedPlan(project, AgentChatSemanticNavigationDirection.NEXT)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal class AgentChatPreviousProposedPlanAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    navigateSelectedAgentChatProposedPlan(project, AgentChatSemanticNavigationDirection.PREVIOUS)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null &&
                                       canNavigateSelectedAgentChatProposedPlan(project, AgentChatSemanticNavigationDirection.PREVIOUS)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
