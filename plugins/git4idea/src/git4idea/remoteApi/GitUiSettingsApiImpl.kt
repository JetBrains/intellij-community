// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.dvcs.branch.DvcsBranchSyncPolicyUpdateNotifier
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.platform.project.ProjectId
import com.intellij.vcs.git.rpc.GitUiSettingsApi
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScoped
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.coroutineScope

internal class GitUiSettingsApiImpl : GitUiSettingsApi {
  override suspend fun setGroupingByPrefix(projectId: ProjectId, groupByPrefix: Boolean) = projectScoped(projectId) { project ->
    requireOwner()
    GitVcsSettings.getInstance(project).setBranchGroupingSettings(GroupingKey.GROUPING_BY_DIRECTORY, groupByPrefix)
    coroutineScope {
      saveSettingsForRemoteDevelopment(this, project)
    }
  }

  override suspend fun initBranchSyncPolicyIfNotInitialized(projectId: ProjectId) = projectScoped(projectId) { project ->
    requireOwner()
    DvcsBranchSyncPolicyUpdateNotifier(project,
                                       GitVcsSettings.getInstance(project),
                                       GitRepositoryManager.getInstance(project)).initBranchSyncPolicyIfNotInitialized()
  }

  override suspend fun setShowTags(projectId: ProjectId, showTags: Boolean) = projectScoped(projectId) { project ->
    requireOwner()
    GitVcsSettings.getInstance(project).setShowTags(showTags)
    coroutineScope {
      saveSettingsForRemoteDevelopment(this, project)
    }
  }
}