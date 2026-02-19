// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.IntelliJProjectUtil

internal sealed class UpdateIdeFromSourcesActionBase(private val forceShowSettings: Boolean = false) : DumbAwareAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val settings = UpdateFromSourcesSettings.getState()

    if (forceShowSettings || settings.showSettings) {
      val oldWorkIdePath = settings.actualIdePath
      val ok = UpdateFromSourcesDialog(project, forceShowSettings).showAndGet()
      if (!ok) {
        return
      }

      val updatedState = settings
      if (oldWorkIdePath != updatedState.actualIdePath) {
        updatedState.workIdePathsHistory.remove(oldWorkIdePath)
        updatedState.workIdePathsHistory.remove(updatedState.actualIdePath)
        updatedState.workIdePathsHistory.add(0, updatedState.actualIdePath)
        updatedState.workIdePathsHistory.add(0, oldWorkIdePath)
      }
    }

    updateFromSources(project, beforeRestart = { }, settings.restartAutomatically)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && IntelliJProjectUtil.isIntelliJPlatformProject(project)
  }
}

internal class UpdateIdeFromSourcesSettingsAction : UpdateIdeFromSourcesActionBase(forceShowSettings = true) {
  override fun update(e: AnActionEvent) {
    super.update(e)
    val presentation = e.presentation
    if (presentation.isVisible) {
      presentation.description = ActionManager.getInstance().getAction("UpdateIdeFromSources").templatePresentation.description
    }
  }
}

internal class UpdateIdeFromSourcesAction : UpdateIdeFromSourcesActionBase(forceShowSettings = false)
