// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareToggleAction

internal class TogglePresentationAssistantAction : DumbAwareToggleAction() {
  init {
    templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.IfRequested
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent) = service<PresentationAssistant>().configuration.showActionDescriptions

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val presentationAssistant = service<PresentationAssistant>()
    presentationAssistant.configuration.showActionDescriptions = state
    presentationAssistant.updatePresenter(e.project, true)
  }
}