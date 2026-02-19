// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.dvcs.repo.repositoryId
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.openapi.project.Project
import com.intellij.vcs.git.branch.popup.GitBranchesPopupKeys
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryIdCache

private typealias SharedDataKeys = GitBranchesPopupKeys

internal class GitBackendBranchesWidgetUiDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val project = snapshot[CommonDataKeys.PROJECT] ?: return

    val sharedAffectedRepositories = snapshot[SharedDataKeys.AFFECTED_REPOSITORIES]
    val sharedSelectedRepository = snapshot[SharedDataKeys.SELECTED_REPOSITORY]

    val backendAffectedRepositories = snapshot[GitBranchActionsDataKeys.AFFECTED_REPOSITORIES]
    val backendSelectedRepository = snapshot[GitBranchActionsDataKeys.SELECTED_REPOSITORY]

    val hasShared = sharedAffectedRepositories != null || sharedSelectedRepository != null
    val hasBackend = backendAffectedRepositories != null || backendSelectedRepository != null

    when {
      hasShared && !hasBackend -> convertSharedToBackend(project, sink, sharedSelectedRepository, sharedAffectedRepositories)
      hasBackend && !hasShared -> convertBackendToShared(project, sink, backendSelectedRepository, backendAffectedRepositories)
    }
  }

  private fun convertSharedToBackend(
    project: Project,
    sink: DataSink,
    sharedSelectedRepository: GitRepositoryModel?,
    sharedAffectedRepositories: List<GitRepositoryModel>?,
  ) {
    val repositoryIdCache = GitRepositoryIdCache.getInstance(project)
    sink[GitBranchActionsDataKeys.SELECTED_REPOSITORY] = sharedSelectedRepository?.repositoryId?.let(repositoryIdCache::get)
    sink[GitBranchActionsDataKeys.AFFECTED_REPOSITORIES] = sharedAffectedRepositories?.mapNotNull { repositoryIdCache.get(it.repositoryId) }
  }

  private fun convertBackendToShared(
    project: Project,
    sink: DataSink,
    backendSelectedRepository: GitRepository?,
    backendAffectedRepositories: List<GitRepository>?,
  ) {
    val repositoriesHolder = GitRepositoriesHolder.getInstance(project)
    if (!repositoriesHolder.initialized) return
    sink[SharedDataKeys.SELECTED_REPOSITORY] = backendSelectedRepository?.repositoryId()?.let(repositoriesHolder::get)
    sink[SharedDataKeys.AFFECTED_REPOSITORIES] = backendAffectedRepositories?.mapNotNull { repositoriesHolder.get(it.repositoryId()) }
  }
}