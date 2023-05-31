// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.flow.*

internal class GHPRBranchesViewModel(
  project: Project,
  branchesModel: GHPRBranchesModel
) : CodeReviewBranchesViewModel {
  private val _targetBranch: MutableStateFlow<String> = MutableStateFlow(branchesModel.baseBranch)
  override val targetBranch: StateFlow<String> = _targetBranch.asStateFlow()

  private val _sourceBranch: MutableStateFlow<String> = MutableStateFlow(branchesModel.headBranch)
  override val sourceBranch: StateFlow<String> = _sourceBranch.asStateFlow()

  override val isCheckedOut: Flow<Boolean> = callbackFlow {
    val cs = this
    send(isBranchCheckedOut(branchesModel.localRepository, sourceBranch.value))

    project.messageBus
      .connect(cs)
      .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
        trySend(isBranchCheckedOut(it, sourceBranch.value))
      })

    _sourceBranch.collect { sourceBranch ->
      send(isBranchCheckedOut(branchesModel.localRepository, sourceBranch))
    }
  }

  init {
    branchesModel.addAndInvokeChangeListener {
      _targetBranch.value = branchesModel.baseBranch
      _sourceBranch.value = branchesModel.headBranch
    }
  }

  private fun isBranchCheckedOut(repository: GitRepository, sourceBranch: String): Boolean {
    val currentBranchName = repository.currentBranchName
    return currentBranchName == sourceBranch
  }
}