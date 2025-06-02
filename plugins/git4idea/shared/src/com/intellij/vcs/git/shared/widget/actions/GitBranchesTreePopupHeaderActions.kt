// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.widget.actions

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.ui.ExperimentalUI
import com.intellij.vcs.git.shared.widget.tree.GitBranchesTreeFilters
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class GitBranchesTreePopupSettings : DefaultActionGroup(), DumbAware, ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  init {
    templatePresentation.text = DvcsBundle.message("action.BranchActionGroupPopup.settings.text")
    templatePresentation.isPopupGroup = true
    templatePresentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.icon = if (ExperimentalUI.isNewUI()) AllIcons.General.Settings else AllIcons.Actions.More
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class GitBranchesTreePopupResizeAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  init {
    templatePresentation.text = DvcsBundle.message("action.BranchActionGroupPopup.Anonymous.text.restore.size")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val popup = e.getData(GitBranchesWidgetKeys.POPUP)

    val enabledAndVisible = project != null && popup != null
    e.presentation.isEnabledAndVisible = enabledAndVisible
    e.presentation.isEnabled = enabledAndVisible && popup.userResized
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getData(GitBranchesWidgetKeys.POPUP)?.restoreDefaultSize()
  }
}

internal class GitBranchesTreePopupTrackReposSynchronouslyAction : GitWidgetSettingsToggleAction(requireMultiRoot = true) {
  init {
    templatePresentation.text = DvcsBundle.message("sync.setting")
  }

  override fun isSelected(project: Project, settings: GitVcsSettings): Boolean {
    return settings.syncSetting == DvcsSyncSettings.Value.SYNC
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    changeSetting(project) { api ->
      api.setSyncOperationsOnAllRepos(project.projectId(), state)
    }
  }
}

internal class GitBranchesTreePopupGroupByPrefixAction : GitWidgetSettingsToggleAction() {
  override fun isSelected(project: Project, settings: GitVcsSettings): Boolean {
    return settings.branchSettings.isGroupingEnabled(GroupingKey.GROUPING_BY_DIRECTORY)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    val widgetPopup = e.getData(GitBranchesWidgetKeys.POPUP) ?: return

    changeSetting(project, refreshWidget = false) { api ->
      api.setGroupingByPrefix(project.projectId(), state)
      withContext(Dispatchers.EDT) {
        widgetPopup.setGroupingByPrefix(state)
      }
    }
  }
}

internal class GitBranchesTreePopupShowRecentBranchesAction : GitWidgetSettingsToggleAction() {
  override fun isSelected(project: Project, settings: GitVcsSettings): Boolean {
    return settings.showRecentBranches()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    changeSetting(project) { api ->
      api.setShowRecentBranches(project.projectId(), state)
    }
  }
}

internal class GitBranchesTreePopupFilterSeparatorWithText :
  DefaultActionGroup(), DumbAware, ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  init {
    addSeparator(GitBundle.message("separator.git.branches.popup.filter.by"))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = GitWidgetSettingsToggleAction.isEnabledAndVisible(e, requireMultiRoot = true)
  }
}

internal class GitBranchesTreePopupFilterByAction : GitWidgetSettingsToggleAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    if (!isEnabledAndVisible(e, requireMultiRoot = true)) {
      e.presentation.text = GitBundle.message("action.git.branches.popup.filter.by.action.single.text")
    }
  }

  override fun isSelected(project: Project, settings: GitVcsSettings): Boolean = GitBranchesTreeFilters.byActions(project)

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    changeSetting(project) { api ->
      api.setFilteringByActions(project.projectId(), state)
    }
  }

}

internal class GitBranchesTreePopupFilterByRepository : GitWidgetSettingsToggleAction(requireMultiRoot = true) {
  override fun isSelected(project: Project, settings: GitVcsSettings): Boolean = GitBranchesTreeFilters.byRepositoryName(project)

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    changeSetting(project) { api ->
      api.setFilteringByRepositories(project.projectId(), state)
    }
  }
}
