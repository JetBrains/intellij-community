// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.branch.TrackReposSynchronouslyAction
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.popup.KeepingPopupOpenAction
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.config.GitVcsSettings

internal class GitBranchesTreePopupSettings :
  DefaultActionGroup(DvcsBundle.messagePointer("action.BranchActionGroupPopup.settings.text"), true) {
  init {
    templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
  }
}

internal class GitBranchesTreePopupResizeAction :
  DumbAwareAction(DvcsBundle.messagePointer("action.BranchActionGroupPopup.Anonymous.text.restore.size")) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val popup = e.getData(GitBranchesTreePopup.POPUP_KEY)

    val enabledAndVisible = project != null && popup != null
    e.presentation.isEnabledAndVisible = enabledAndVisible
    e.presentation.isEnabled = enabledAndVisible && popup!!.userResized
  }

  override fun actionPerformed(e: AnActionEvent) {
    val popup = e.getData(GitBranchesTreePopup.POPUP_KEY) ?: return

    popup.restoreDefaultSize()
  }
}

internal class GitBranchesTreePopupTrackReposSynchronouslyAction : TrackReposSynchronouslyAction(), KeepingPopupOpenAction {
  override fun getActionUpdateThread(): ActionUpdateThread  = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val projectExist = e.project != null
    if (projectExist) {
      super.update(e)
    }

    val repositories = e.getData(GitBranchActionsUtil.REPOSITORIES_KEY)

    e.presentation.isEnabledAndVisible = projectExist && repositories.orEmpty().size > 1
  }

  override fun getSettings(e: AnActionEvent): DvcsSyncSettings = GitVcsSettings.getInstance(e.project!!)
}

internal class GitBranchesTreePopupGroupByPrefixAction : BranchGroupingAction(GroupingKey.GROUPING_BY_DIRECTORY), KeepingPopupOpenAction {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.setText(DvcsBundle.messagePointer("action.text.branch.group.by.prefix"))
  }
}
