// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.vcs.git.branch.popup.GitBranchesPopupKeys
import git4idea.config.GitVcsSettings
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object GitGroupBranchDataKeys {
  val MULTIPLE_REPOSITORIES: DataKey<Boolean> = DataKey.create("Vcs.Git.Multiple.Repositories")
}

internal class GitGroupBranchByDirectoryAction :
  ToggleAction(),
  ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend,
  DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    val widgetPopup = e.getData(GitBranchesPopupKeys.POPUP)

    return widgetPopup?.groupByDirectory ?: isGroupingEnabled(
      key = GroupingKey.GROUPING_BY_DIRECTORY,
      project = project,
    )
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    val widgetPopup = e.getData(GitBranchesPopupKeys.POPUP)

    saveGrouping(
      coroutineScope = e.coroutineScope,
      key = GroupingKey.GROUPING_BY_DIRECTORY,
      project = project,
      state = state,
    )
    widgetPopup?.groupByDirectory = state
  }
}

internal class GitGroupBranchByRepositoryAction :
  ToggleAction(),
  ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend,
  DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = e.getData(GitGroupBranchDataKeys.MULTIPLE_REPOSITORIES) ?: false
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false

    return isGroupingEnabled(
      key = GroupingKey.GROUPING_BY_REPOSITORY,
      project = project,
    )
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return

    saveGrouping(
      coroutineScope = e.coroutineScope,
      key = GroupingKey.GROUPING_BY_REPOSITORY,
      project = project,
      state = state,
    )
  }
}

private fun isGroupingEnabled(
  key: GroupingKey,
  project: Project,
): Boolean {
  val settings = GitVcsSettings.getInstance(project)
  return settings.branchSettings.isGroupingEnabled(key)
}

private fun saveGrouping(
  coroutineScope: CoroutineScope,
  key: GroupingKey,
  project: Project,
  state: Boolean,
) {
  val settings = GitVcsSettings.getInstance(project)
  settings.setBranchGroupingSettings(key, state)
  saveSettingsForRemoteDevelopment(coroutineScope, project)
}
