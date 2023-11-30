// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil

internal open class UpdateIdeFromSourcesAction
@JvmOverloads constructor(private val forceShowSettings: Boolean = false)
  : AnAction(if (forceShowSettings) DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.show.settings.text")
             else DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.text"),
             DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.description"), null), DumbAware {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (forceShowSettings || UpdateFromSourcesSettings.getState().showSettings) {
      val oldWorkIdePath = UpdateFromSourcesSettings.getState().actualIdePath
      val ok = UpdateFromSourcesDialog(project, forceShowSettings).showAndGet()
      if (!ok) {
        return
      }

      val updatedState = UpdateFromSourcesSettings.getState()
      if (oldWorkIdePath != updatedState.actualIdePath) {
        updatedState.workIdePathsHistory.remove(oldWorkIdePath)
        updatedState.workIdePathsHistory.remove(updatedState.actualIdePath)
        updatedState.workIdePathsHistory.add(0, updatedState.actualIdePath)
        updatedState.workIdePathsHistory.add(0, oldWorkIdePath)
      }
    }

    updateFromSources(project, {}, {
      Messages.showErrorDialog(project, it, CommonBundle.getErrorTitle())
    }, UpdateFromSourcesSettings.getState().restartAutomatically)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && PsiUtil.isIdeaProject(project)
  }
}

internal class UpdateIdeFromSourcesSettingsAction : UpdateIdeFromSourcesAction(true)
