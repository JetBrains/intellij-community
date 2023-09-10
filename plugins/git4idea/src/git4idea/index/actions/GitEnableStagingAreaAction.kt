// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import git4idea.GitVcs
import git4idea.config.GitVcsApplicationSettings
import git4idea.index.canEnableStagingArea
import git4idea.index.enableStagingArea

abstract class GitToggleStagingAreaAction(private val enable: Boolean) : DumbAwareAction(), RightAlignedToolbarAction {

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null || !canEnableStagingArea() ||
        ProjectLevelVcsManager.getInstance(project).singleVCS?.keyInstanceMethod != GitVcs.getKey()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = GitVcsApplicationSettings.getInstance().isStagingAreaEnabled != enable
  }

  override fun actionPerformed(e: AnActionEvent) {
    enableStagingArea(!GitVcsApplicationSettings.getInstance().isStagingAreaEnabled)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}

class GitEnableStagingAreaAction : GitToggleStagingAreaAction(true) {
  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    if (e.presentation.isEnabled && project != null && ChangeListManager.getInstance(project).changeListsNumber > 1) {
      e.presentation.isEnabledAndVisible = false
    }
  }
}

class GitDisableStagingAreaAction : GitToggleStagingAreaAction(false)