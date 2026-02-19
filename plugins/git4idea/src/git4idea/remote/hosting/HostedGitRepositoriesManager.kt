// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import git4idea.GitRemoteBranch
import git4idea.repo.GitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

interface HostedGitRepositoriesManager<M : HostedGitRepositoryMapping> {
  val knownRepositoriesState: StateFlow<Set<M>>
}

val <M : HostedGitRepositoryMapping> HostedGitRepositoriesManager<M>.knownRepositories: Set<M>
  get() = knownRepositoriesState.value

fun <M : HostedGitRepositoryMapping> HostedGitRepositoriesManager<M>.findKnownRepositories(repository: GitRepository): List<M> =
  knownRepositories.filter {
    it.remote.repository == repository
  }

fun <M : HostedGitRepositoryMapping> HostedGitRepositoriesManager<M>.findHostedRemoteBranchTrackedByCurrent(repository: GitRepository)
  : Flow<Pair<M, GitRemoteBranch>?> =
  knownRepositoriesState.combine(repository.currentRemoteBranchFlow()) { repositories, branch ->
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