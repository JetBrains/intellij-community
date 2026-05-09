// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface AgentPromptPalettePopupSession {
  fun show()
  fun requestFocus()
  fun requestComposerFocus()
  fun isVisible(): Boolean
  fun applyAddContext(request: AgentPromptAddContextRequest): AgentPromptAddContextApplyResult
}

internal fun interface AgentPromptPalettePopupFactory {
  fun create(
    invocationData: AgentPromptInvocationData,
    initialAddContextRequest: AgentPromptAddContextRequest?,
    onClosed: () -> Unit,
  ): AgentPromptPalettePopupSession
}

@Service(Service.Level.PROJECT)
internal class AgentPromptPalettePopupService {
  private val controller = AgentPromptPalettePopupController()

  @RequiresEdt
  fun show(invocationData: AgentPromptInvocationData) {
    controller.show(invocationData)
  }

  @RequiresEdt
  fun showAddContext(invocationData: AgentPromptInvocationData, request: AgentPromptAddContextRequest) {
    controller.showAddContext(invocationData, request)
  }

  suspend fun showAddContextFromUiDispatcher(invocationData: AgentPromptInvocationData, request: AgentPromptAddContextRequest) {
    controller.showAddContextFromUiDispatcher(invocationData, request)
  }

  @RequiresEdt
  fun applyAddContextToVisible(request: AgentPromptAddContextRequest): AgentPromptAddContextApplyResult? {
    return controller.applyAddContextToVisible(request)
  }
}

internal class AgentPromptPalettePopupController(
  private val popupFactory: AgentPromptPalettePopupFactory = AgentPromptPalettePopupFactory { invocationData, initialAddContextRequest, onClosed ->
    AgentPromptPalettePopup(
      invocationData = invocationData,
      initialAddContextRequest = initialAddContextRequest,
      onClosed = onClosed,
    )
  },
) {
  private var activePopup: AgentPromptPalettePopupSession? = null

  @RequiresEdt
  fun show(invocationData: AgentPromptInvocationData) {
    showPopup(invocationData = invocationData, initialAddContextRequest = null)
  }

  @RequiresEdt
  fun showAddContext(invocationData: AgentPromptInvocationData, request: AgentPromptAddContextRequest) {
    val currentPopup = activePopup
    if (currentPopup?.isVisible() == true) {
      currentPopup.applyAddContext(request)
      currentPopup.requestComposerFocus()
      return
    }

    showPopup(invocationData = invocationData, initialAddContextRequest = request)
  }

  suspend fun showAddContextFromUiDispatcher(invocationData: AgentPromptInvocationData, request: AgentPromptAddContextRequest) {
    if (applyAddContextToVisible(request) != null) {
      return
    }

    withContext(Dispatchers.UiWithModelAccess) {
      showAddContext(invocationData, request)
    }
  }

  @RequiresEdt
  fun applyAddContextToVisible(request: AgentPromptAddContextRequest): AgentPromptAddContextApplyResult? {
    val currentPopup = activePopup
    if (currentPopup?.isVisible() == true) {
      val result = currentPopup.applyAddContext(request)
      currentPopup.requestComposerFocus()
      return result
    }

    return null
  }

  @RequiresEdt
  private fun showPopup(
    invocationData: AgentPromptInvocationData,
    initialAddContextRequest: AgentPromptAddContextRequest?,
  ) {
    val currentPopup = activePopup
    if (currentPopup?.isVisible() == true) {
      currentPopup.requestFocus()
      return
    }

    lateinit var popupToShow: AgentPromptPalettePopupSession
    popupToShow = popupFactory.create(invocationData, initialAddContextRequest) {
      if (activePopup === popupToShow) {
        activePopup = null
      }
    }

    activePopup = popupToShow
    popupToShow.show()
  }
}
