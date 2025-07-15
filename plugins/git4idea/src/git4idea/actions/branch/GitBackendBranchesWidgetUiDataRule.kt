// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.vcs.git.branch.popup.GitBranchesPopupKeys
import git4idea.repo.GitRepositoryIdCache

internal class GitBackendBranchesWidgetUiDataRule: UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val affectedRepositories = snapshot[GitBranchesPopupKeys.AFFECTED_REPOSITORIES]
    val selectedRepository = snapshot[GitBranchesPopupKeys.SELECTED_REPOSITORY]

    if (affectedRepositories == null && selectedRepository == null) return

    val project = snapshot[CommonDataKeys.PROJECT] ?: return

    val repositoryIdCache = GitRepositoryIdCache.getInstance(project)

    sink[GitBranchActionsDataKeys.SELECTED_REPOSITORY] = selectedRepository?.repositoryId?.let { repositoryIdCache.get(it) }
    sink[GitBranchActionsDataKeys.AFFECTED_REPOSITORIES] = affectedRepositories?.mapNotNull { repositoryIdCache.get(it.repositoryId) }
  }
}