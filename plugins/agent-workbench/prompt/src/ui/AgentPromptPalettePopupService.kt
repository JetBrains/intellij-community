// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.annotations.RequiresEdt

internal interface AgentPromptPalettePopupSession {
  fun show()
  fun requestFocus()
  fun isVisible(): Boolean
}

internal fun interface AgentPromptPalettePopupFactory {
  fun create(invocationData: AgentPromptInvocationData, onClosed: () -> Unit): AgentPromptPalettePopupSession
}

@Service(Service.Level.PROJECT)
internal class AgentPromptPalettePopupService {
  private val controller = AgentPromptPalettePopupController()

  @RequiresEdt
  fun show(invocationData: AgentPromptInvocationData) {
    controller.show(invocationData)
  }
}

internal class AgentPromptPalettePopupController(
  private val popupFactory: AgentPromptPalettePopupFactory = AgentPromptPalettePopupFactory { invocationData, onClosed ->
    AgentPromptPalettePopup(invocationData = invocationData, onClosed = onClosed)
  },
) {
  private var activePopup: AgentPromptPalettePopupSession? = null

  @RequiresEdt
  fun show(invocationData: AgentPromptInvocationData) {
    val currentPopup = activePopup
    if (currentPopup?.isVisible() == true) {
      currentPopup.requestFocus()
      return
    }

    lateinit var popupToShow: AgentPromptPalettePopupSession
    popupToShow = popupFactory.create(invocationData) {
      if (activePopup === popupToShow) {
        activePopup = null
      }
    }

    activePopup = popupToShow
    popupToShow.show()
  }
}
