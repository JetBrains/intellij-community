// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.dvcs.branch.DvcsBranchSyncPolicyUpdateNotifier
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.progress.withCurrentThreadCoroutineScope
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.vcs.git.rpc.GitUiSettingsApi
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepositoryManager

internal class GitUiSettingsApiImpl : GitUiSettingsApi {
  override suspend fun setGroupingByPrefix(projectId: ProjectId, groupByPrefix: Boolean) {
    requireOwner()
    val project = projectId.findProjectOrNull() ?: return
    GitVcsSettings.getInstance(project).setBranchGroupingSettings(GroupingKey.GROUPING_BY_DIRECTORY, groupByPrefix)
    withCurrentThreadCoroutineScope {
      saveSettingsForRemoteDevelopment(project)
    }
  }

  override suspend fun initBranchSyncPolicyIfNotInitialized(projectId: ProjectId) {
    requireOwner()
    val project = projectId.findProjectOrNull() ?: return

    DvcsBranchSyncPolicyUpdateNotifier(project,
                                       GitVcsSettings.getInstance(project),
                                       GitRepositoryManager.getInstance(project)).initBranchSyncPolicyIfNotInitialized()
  }

  override suspend fun setShowTags(projectId: ProjectId, showTags: Boolean) {
    val project = projectId.findProjectOrNull() ?: return
    GitVcsSettings.getInstance(project).setShowTags(showTags)
    withCurrentThreadCoroutineScope {
      saveSettingsForRemoteDevelopment(project)
    }
  }
}