// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil

internal class AgentWorkbenchPromptShortcutActionPromoter : ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    if (CommonDataKeys.PROJECT.getData(context) == null || CommonDataKeys.EDITOR.getData(context) == null) {
      return emptyList()
    }

    val actionManager = ActionManager.getInstance()
    var promptAction: AnAction? = null
    var aiAssistantEditorActionPresent = false

    for (action in actions) {
      when (actionManager.getId(ActionUtil.getDelegateChainRootAction(action))) {
        PROMPT_ACTION_ID -> {
          if (promptAction == null) {
            promptAction = action
          }
        }
        AI_ASSISTANT_EDITOR_ACTION_ID -> {
          aiAssistantEditorActionPresent = true
        }
      }

      if (promptAction != null && aiAssistantEditorActionPresent) {
        return listOf(promptAction)
      }
    }

    return emptyList()
  }

  internal companion object {
    const val PROMPT_ACTION_ID: String = "AgentWorkbenchPrompt.OpenGlobalPalette"
    const val AUTO_SELECT_ACTION_ID: String = "AgentWorkbenchPrompt.OpenGlobalPaletteAutoSelect"
    const val AI_ASSISTANT_EDITOR_ACTION_ID: String = "AIAssistant.Editor.AskAiAssistantInEditor"
  }
}
