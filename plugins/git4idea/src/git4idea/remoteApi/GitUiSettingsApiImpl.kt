// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.components.service
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.vcs.git.shared.rpc.GitUiSettingsApi
import git4idea.config.GitVcsSettings
import git4idea.ui.branch.GitBranchManager

internal class GitUiSettingsApiImpl : GitUiSettingsApi {
  override suspend fun setGroupingByPrefix(projectId: ProjectId, groupByPrefix: Boolean) {
    val project = projectId.findProjectOrNull() ?: return
    project.service<GitBranchManager>().setGrouping(GroupingKey.GROUPING_BY_DIRECTORY, groupByPrefix)
  }

  override suspend fun setShowRecentBranches(projectId: ProjectId, displayRecent: Boolean) {
    val project = projectId.findProjectOrNull() ?: return
    GitVcsSettings.getInstance(project).setShowRecentBranches(displayRecent)
  }

  override suspend fun setFilteringByActions(projectId: ProjectId, allowFilteringByActions: Boolean) {
    val project = projectId.findProjectOrNull() ?: return
    GitVcsSettings.getInstance(project).setFilterByActionInPopup(allowFilteringByActions)
  }

  override suspend fun setFilteringByRepositories(projectId: ProjectId, allowFilteringByRepositories: Boolean) {
    val project = projectId.findProjectOrNull() ?: return
    GitVcsSettings.getInstance(project).setFilterByRepositoryInPopup(allowFilteringByRepositories)
  }

  override suspend fun setSyncOperationsOnAllRepos(projectId: ProjectId, shouldSync: Boolean) {
    val project = projectId.findProjectOrNull() ?: return
    GitVcsSettings.getInstance(project).syncSetting = if (shouldSync) DvcsSyncSettings.Value.SYNC else DvcsSyncSettings.Value.DONT_SYNC
  }

  override suspend fun setShowTags(projectId: ProjectId, showTags: Boolean) {
    val project = projectId.findProjectOrNull() ?: return
    GitVcsSettings.getInstance(project).setShowTags(showTags)
  }
}