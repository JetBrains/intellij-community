// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.actions

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md

import com.intellij.agent.workbench.prompt.context.AGENT_PROMPT_INVOCATION_ACTION_EVENT_KEY
import com.intellij.agent.workbench.prompt.ui.AgentPromptPalettePopup
import com.intellij.agent.workbench.sessions.core.prompt.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

internal class AgentWorkbenchGlobalPromptAction : AnAction(), DumbAware {
  companion object {
    private const val ACTION_ID: String = "AgentWorkbenchPrompt.OpenGlobalPalette"
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    AgentPromptPalettePopup(
      invocationData = AgentPromptInvocationData(
        project = project,
        actionId = ACTION_ID,
        actionText = e.presentation.text,
        actionPlace = e.place,
        invokedAtMs = System.currentTimeMillis(),
        attributes = mapOf(
          AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY to e.dataContext,
          AGENT_PROMPT_INVOCATION_ACTION_EVENT_KEY to e,
        ),
      )
    ).show()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
