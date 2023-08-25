// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import git4idea.GitVcs
import git4idea.config.GitVcsApplicationSettings
import git4idea.i18n.GitBundle
import git4idea.index.canEnableStagingArea
import git4idea.index.enableStagingArea

class GitEnableStagingAreaAction : DumbAwareAction(), RightAlignedToolbarAction {

  init {
    updatePresentation(templatePresentation, false)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null || !canEnableStagingArea() ||
        ProjectLevelVcsManager.getInstance(project).singleVCS?.keyInstanceMethod != GitVcs.getKey()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val isStagingAreaEnabled = GitVcsApplicationSettings.getInstance().isStagingAreaEnabled
    if (!isStagingAreaEnabled && ChangeListManager.getInstance(project).changeListsNumber > 1) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    updatePresentation(e.presentation, isStagingAreaEnabled)
  }

  override fun actionPerformed(e: AnActionEvent) {
    enableStagingArea(!GitVcsApplicationSettings.getInstance().isStagingAreaEnabled)
  }

  private fun updatePresentation(presentation: Presentation, isStagingAreaEnabled: Boolean) {
    if (isStagingAreaEnabled) {
      presentation.text = GitBundle.message("action.Git.Stage.Enable.Enabled.text")
      presentation.description = GitBundle.message("action.Git.Stage.Enable.Enabled.description")
      presentation.icon = AllIcons.Vcs.Changelist
    }
    else {
      presentation.text = GitBundle.message("action.Git.Stage.Enable.Disabled.text")
      presentation.description = GitBundle.message("action.Git.Stage.Enable.Disabled.description")
      presentation.icon = AllIcons.Ide.Gift
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}