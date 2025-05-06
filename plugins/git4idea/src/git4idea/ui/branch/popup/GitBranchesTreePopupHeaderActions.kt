// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.branch.TrackReposSynchronouslyAction
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.ui.ExperimentalUI
import git4idea.GitUtil
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.ui.branch.BranchGroupingAction

internal class GitBranchesTreePopupSettings :
  DefaultActionGroup(DvcsBundle.messagePointer("action.BranchActionGroupPopup.settings.text"), true), DumbAware {
  init {
    templatePresentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.icon = if (ExperimentalUI.isNewUI()) AllIcons.General.Settings else AllIcons.Actions.More
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal class GitBranchesTreePopupResizeAction :
  DumbAwareAction(DvcsBundle.messagePointer("action.BranchActionGroupPopup.Anonymous.text.restore.size")) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val popup = e.getData(GitBranchesTreePopupBase.POPUP_KEY)

    val enabledAndVisible = project != null && popup != null
    e.presentation.isEnabledAndVisible = enabledAndVisible
    e.presentation.isEnabled = enabledAndVisible && popup!!.userResized
  }

  override fun actionPerformed(e: AnActionEvent) {
    val popup = e.getData(GitBranchesTreePopupBase.POPUP_KEY) ?: return

    popup.restoreDefaultSize()
  }
}

internal class GitBranchesTreePopupTrackReposSynchronouslyAction : TrackReposSynchronouslyAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val projectExist = e.project != null
    if (projectExist) {
      super.update(e)
    }

    val repositories = e.getData(GitBranchActionsDataKeys.AFFECTED_REPOSITORIES)

    e.presentation.isEnabledAndVisible = projectExist && repositories.orEmpty().size > 1
  }

  override fun getSettings(e: AnActionEvent): DvcsSyncSettings = GitVcsSettings.getInstance(e.project!!)
}

internal class GitBranchesTreePopupGroupByPrefixAction : BranchGroupingAction(GroupingKey.GROUPING_BY_DIRECTORY) {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.setText(DvcsBundle.messagePointer("action.text.branch.group.by.prefix"))
  }
}

internal class GitBranchesTreePopupShowRecentBranchesAction :
  ToggleAction(GitBundle.messagePointer("git.branches.popup.show.recent.branches.action.name")), DumbAware {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.project != null
                                         && e.getData(GitBranchesTreePopupBase.POPUP_KEY) != null
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean =
    e.project?.let(GitVcsSettings::getInstance)?.showRecentBranches() ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return

    GitVcsSettings.getInstance(project).setShowRecentBranches(state)
    e.getRequiredData(GitBranchesTreePopupBase.POPUP_KEY).refresh()
  }
}


internal class GitBranchesTreePopupFilterSeparatorWithText : DefaultActionGroup(), DumbAware {

  init {
    addSeparator(GitBundle.message("separator.git.branches.popup.filter.by"))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val enabledAndVisible =
      GitBranchesTreePopupFilterByRepository.isEnabledAndVisible(e) && GitBranchesTreePopupFilterByAction.isEnabledAndVisible(e)
    e.presentation.isEnabledAndVisible = enabledAndVisible
  }
}

internal class GitBranchesTreePopupFilterByAction : DumbAwareToggleAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (!GitBranchesTreePopupFilterByRepository.isEnabledAndVisible(e)) {
      e.presentation.text = GitBundle.message("action.git.branches.popup.filter.by.action.single.text")
    }
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
  }

  override fun isSelected(e: AnActionEvent): Boolean = Companion.isSelected(e.project)

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return

    GitVcsSettings.getInstance(project).setFilterByActionInPopup(state)
    e.getRequiredData(GitBranchesTreePopupBase.POPUP_KEY).refresh()
  }

  companion object {
    fun isSelected(project: Project?): Boolean {
      return project != null && project.let(GitVcsSettings::getInstance).filterByActionInPopup()
    }

    fun isEnabledAndVisible(e: AnActionEvent): Boolean {
      return e.project != null
             && e.getData(GitBranchesTreePopupBase.POPUP_KEY) != null
    }
  }
}

internal class GitBranchesTreePopupFilterByRepository : DumbAwareToggleAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
  }

  override fun isSelected(e: AnActionEvent): Boolean = Companion.isSelected(e.project)

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return

    GitVcsSettings.getInstance(project).setFilterByRepositoryInPopup(state)
    e.getRequiredData(GitBranchesTreePopupBase.POPUP_KEY).refresh()
  }

  companion object {
    fun isSelected(project: Project?): Boolean {
      return project != null
             && project.isMultiRoot() && project.let(GitVcsSettings::getInstance).filterByRepositoryInPopup()
    }

    fun isEnabledAndVisible(e: AnActionEvent): Boolean {
      val project = e.project
      return project != null
             && e.getData(GitBranchesTreePopupBase.POPUP_KEY) != null
             && project.isMultiRoot()
    }
  }
}

private fun Project.isMultiRoot() = !GitUtil.justOneGitRepository(this)
