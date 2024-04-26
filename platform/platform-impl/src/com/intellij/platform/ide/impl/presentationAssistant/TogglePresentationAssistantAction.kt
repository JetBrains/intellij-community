// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

internal class TogglePresentationAssistantAction : ToggleAction(name), DumbAware {
  override fun isSelected(e: AnActionEvent) = service<PresentationAssistant>().configuration.showActionDescriptions

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val presentationAssistant = service<PresentationAssistant>()
    presentationAssistant.configuration.showActionDescriptions = state
    presentationAssistant.updatePresenter(e.project, true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  companion object {
    const val ID: String = "TogglePresentationAssistantAction"
    val name: Supplier<@Nls String>
      get() = IdeBundle.messagePointer("presentation.assistant.toggle.action.name")
  }
}