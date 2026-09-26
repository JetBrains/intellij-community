// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import git4idea.GitRemoteBranch
import git4idea.repo.GitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface HostedGitRepositoriesManager<M : HostedGitRepositoryMapping> {
  val knownRepositoriesState: StateFlow<Set<M>>
}

val <M : HostedGitRepositoryMapping> HostedGitRepositoriesManager<M>.knownRepositories: Set<M>
  get() = knownRepositoriesState.value

fun <M : HostedGitRepositoryMapping> HostedGitRepositoriesManager<M>.findKnownRepositories(repository: GitRepository): List<M> =
  knownRepositories.filter {
    it.remote.repository == repository
  }

/**
 * Emits the hosted repository mapping and the remote branch tracked by the current local branch (the branch whose
 * pull/merge request the current branch is reviewed against), or `null` when the current branch tracks no hosted branch.
 *
 * Re-emits not only when the tracked branch changes, but also when its tip commit moves (e.g. after a push): review
 * features rely on this to re-resolve "which PR/MR corresponds to the current branch" without requiring a branch switch.
 * [GitRemoteBranch] equality ignores the commit it points to, so the tip hash is folded into the dedup key explicitly.
 */
fun <M : HostedGitRepositoryMapping> HostedGitRepositoriesManager<M>.findHostedRemoteBranchTrackedByCurrent(repository: GitRepository)
  : Flow<Pair<M, GitRemoteBranch>?> =
  knownRepositoriesState.combine(
    repository.infoFlow()
      .map { info ->
        val branch = info.findFirstRemoteBranchTrackedByCurrent()
        branch to branch?.let { info.remoteBranchesWithHashes[it] }
      }
      .distinctUntilChanged()
  ) { repositories, (branch, _) ->
    if (branch == null) {
      null
    }
    else {
      repositories.find {
        it.remote.repository == repository && it.remote.remote == branch.remote
      }?.let {
        it to branch
      }
    }
  }