// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPromotedActionProvider
import javax.swing.JComponent

internal class AgentWorkbenchGlobalPromptEmptyTextPromotedActionProvider : EditorEmptyTextPromotedActionProvider {
  override fun getPromotedAction(splitters: JComponent): EditorEmptyTextPromotedActionProvider.PromotedAction {
    return EditorEmptyTextPromotedActionProvider.PromotedAction(
      actionId = AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID,
      text = AgentPromptBundle.message("action.AgentWorkbenchPrompt.OpenGlobalPalette.text"),
    )
  }
}
