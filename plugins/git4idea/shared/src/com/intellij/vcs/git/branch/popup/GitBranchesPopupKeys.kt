// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch.popup

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.vcs.git.branch.popup.GitBranchesPopupKeys.AFFECTED_REPOSITORIES
import com.intellij.vcs.git.branch.popup.GitBranchesPopupKeys.SELECTED_REPOSITORY
import com.intellij.vcs.git.repo.GitRepositoryModel
import org.jetbrains.annotations.ApiStatus

/**
 * [SELECTED_REPOSITORY] and [AFFECTED_REPOSITORIES] are "frontend-friendly" alternatives for the corresponding keys from
 * `git4idea.actions.branch.GitBranchActionsDataKeys`.
 * In the split mode these keys are re-mapped for backend-delegating actions
 * (`com.jetbrains.rdclient.actions.base.BackendDelegatingAction`) via
 * timestamp model providers (look for details in `frontend.split` and `backend.split` modules).
 * Thus, on the backend side actions are provided with data keys operating `GitRepository` preserving pre-RD contracts.
 * In the monolith mode [com.intellij.openapi.actionSystem.UiDataRule] is used to provide the backend repositories context.
 */
@ApiStatus.Internal
object GitBranchesPopupKeys {
  val POPUP: DataKey<GitBranchesPopup> = DataKey.create("GIT_BRANCHES_TREE_POPUP")

  val SELECTED_REPOSITORY: DataKey<GitRepositoryModel> = DataKey.create("Git.Widget.Selected.Repository")
  val AFFECTED_REPOSITORIES: DataKey<List<GitRepositoryModel>> = DataKey.create("Git.Frontend.Repositories")
}