// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.ui.AgentPromptPalettePopupService
import com.intellij.agent.workbench.prompt.ui.context.AGENT_PROMPT_INVOCATION_ACTION_EVENT_KEY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

/**
 * Alternate global prompt entrypoint with its own action id and shortcut.
 *
 * Opens the same shared palette as [AgentWorkbenchGlobalPromptAction], but sets
 * [AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY] so the popup can auto-select the first matching extension tab.
 */
internal class AgentWorkbenchGlobalPromptAutoSelectAction : AnAction(), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    project.service<AgentPromptPalettePopupService>().show(
      invocationData = AgentPromptInvocationData(
        project = project,
        actionId = "AgentWorkbenchPrompt.OpenGlobalPaletteAutoSelect",
        actionText = e.presentation.text,
        actionPlace = e.place,
        invokedAtMs = System.currentTimeMillis(),
        attributes = mapOf(
          AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY to e.dataContext,
          AGENT_PROMPT_INVOCATION_ACTION_EVENT_KEY to e,
          AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY to true,
        ),
      )
    )
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
