// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.actionSystem.ex.TooltipLinkProvider
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import git4idea.GitVcs
import git4idea.config.GitVcsApplicationSettings
import git4idea.i18n.GitBundle
import git4idea.index.canEnableStagingArea
import git4idea.index.enableStagingArea
import git4idea.index.ui.GitStagePanel
import javax.swing.JComponent

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

class GitEnableStagingAreaAction : GitToggleStagingAreaAction(true), TooltipDescriptionProvider, TooltipLinkProvider {
  override fun getTooltipLink(owner: JComponent?): TooltipLinkProvider.TooltipLink {
    return TooltipLinkProvider.TooltipLink(GitBundle.message("stage.default.status.help")) {
      HelpManager.getInstance().invokeHelp(GitStagePanel.HELP_ID)
    }
  }
}

class GitDisableStagingAreaAction : GitToggleStagingAreaAction(false)