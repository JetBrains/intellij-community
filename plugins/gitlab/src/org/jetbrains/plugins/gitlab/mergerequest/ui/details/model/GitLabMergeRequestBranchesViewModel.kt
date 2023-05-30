// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest

internal class GitLabMergeRequestBranchesViewModel(
  project: Project,
  mergeRequest: GitLabMergeRequest,
  repository: GitRepository
) : CodeReviewBranchesViewModel {
  override val targetBranch: StateFlow<String> = mergeRequest.targetBranch
  override val sourceBranch: StateFlow<String> = mergeRequest.sourceBranch

  override val isCheckedOut: Flow<Boolean> = callbackFlow {
    val cs = this
    send(isBranchCheckedOut(repository, sourceBranch.value))

    project.messageBus
      .connect(cs)
      .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
        trySend(isBranchCheckedOut(it, sourceBranch.value))
      })

    sourceBranch.collect { sourceBranch ->
      send(isBranchCheckedOut(repository, sourceBranch))
    }
  }

  private fun isBranchCheckedOut(repository: GitRepository, sourceBranch: String): Boolean {
    val currentBranchName = repository.currentBranchName
    return currentBranchName == sourceBranch
  }
}