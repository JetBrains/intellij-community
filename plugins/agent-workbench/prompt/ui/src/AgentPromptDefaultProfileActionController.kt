// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile

internal class AgentPromptDefaultProfileActionController(
  private val actionControl: AgentPromptDefaultProfileActionControl,
  private val actionProvider: () -> AgentPromptDefaultProfileAction?,
  private val onMakeDefault: (AgentPromptLaunchProfile) -> Unit,
  private val onSaveAsDefault: () -> Unit,
) {
  init {
    actionControl.setActionHandler(::performAction)
  }

  fun refreshPresentation(visible: Boolean) {
    val actionState = if (visible) actionProvider() else null
    actionControl.setState(actionState?.controlState())
  }

  private fun performAction() {
    when (val state = actionProvider()) {
      is AgentPromptDefaultProfileAction.MakeDefault -> onMakeDefault(state.profile)
      AgentPromptDefaultProfileAction.SaveAsDefault -> onSaveAsDefault()
      null -> return
    }
  }

  private fun AgentPromptDefaultProfileAction.controlState(): AgentPromptDefaultProfileActionState {
    return when (this) {
      is AgentPromptDefaultProfileAction.MakeDefault -> AgentPromptDefaultProfileActionState.MAKE_DEFAULT
      AgentPromptDefaultProfileAction.SaveAsDefault -> AgentPromptDefaultProfileActionState.SAVE_AS_DEFAULT
    }
  }
}
