// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import git4idea.GitVcs
import git4idea.config.GitVcsApplicationSettings
import git4idea.index.enableStagingArea

abstract class GitToggleStagingAreaAction(private val enable: Boolean) : DumbAwareToggleAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null ||
        ProjectLevelVcsManager.getInstance(project).singleVCS?.keyInstanceMethod != GitVcs.getKey()) {
      e.presentation.isEnabledAndVisible = false
    }
    super.update(e)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return GitVcsApplicationSettings.getInstance().isStagingAreaEnabled == enable
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    enableStagingArea(if (enable) state else !state)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}

class GitEnableStagingAreaAction : GitToggleStagingAreaAction(true)
class GitDisableStagingAreaAction : GitToggleStagingAreaAction(false)