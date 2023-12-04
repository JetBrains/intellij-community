// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup.compose

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import com.intellij.openapi.util.TextRange
import com.intellij.ui.speedSearch.SpeedSearch
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.repo.GitRepository

internal class GitBranchComposeVm(
  repository: GitRepository,
  branch: GitBranch,
  incomingOutgoingManager: GitBranchIncomingOutgoingManager,
  val isFavorite: State<Boolean>,
  private val toggleIsFavoriteState: () -> Unit,
  val isCurrent: Boolean,
  private val speedSearch: State<SpeedSearch>,
) {
  val name: String = branch.name

  val matchingFragments: State<List<TextRange>> = derivedStateOf {
    speedSearch.value.matchingFragments(name)?.toList() ?: emptyList()
  }


  val trackedBranch: GitRemoteBranch? = if (branch is GitLocalBranch) {
    branch.findTrackedBranch(repository)
  }
  else {
    null
  }

  // TODO: make it reactive
  val hasIncomings = incomingOutgoingManager.hasIncomingFor(repository, branch.name)
  val hasOutgoings = incomingOutgoingManager.hasOutgoingFor(repository, branch.name)


  fun toggleIsFavourite() {
    toggleIsFavoriteState()
  }
}