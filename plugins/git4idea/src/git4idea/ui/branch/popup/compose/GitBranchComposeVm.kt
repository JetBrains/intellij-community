// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup.compose

import com.intellij.openapi.util.TextRange
import com.intellij.ui.speedSearch.SpeedSearch
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class GitBranchComposeVm(
  coroutineScope: CoroutineScope,
  repository: GitRepository,
  branch: GitBranch,
  speedSearchText: StateFlow<String>,
  incomingOutgoingManager: GitBranchIncomingOutgoingManager,
  val isFavorite: StateFlow<Boolean>,
  private val toggleIsFavoriteState: () -> Unit,
  val isCurrent: Boolean,
  private val speedSearch: SpeedSearch,
) {
  val name: String = branch.name

  val matchingFragments: StateFlow<List<TextRange>> = speedSearchText.map {
    speedSearch.matchingFragments(name)?.toList() ?: emptyList()
  }.stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

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