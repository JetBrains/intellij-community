// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.util.URIUtil
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchUtil
import git4idea.remote.hosting.HostedGitRepositoryMapping
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitRepositoryMappingData

object GitPushNotificationUtil {
  /**
   * Find a matching repository mapping and account for a [remote] in a [repository]
   *
   * A matching repository mapping is found in [knownMappings] and then an account is chosen with the following priorities:
   * 1. Saved mapping and account [savedAccount]
   * 2. Default account [defaultAccount]
   * 3. A singular account from [accounts]
   */
  fun <M, A> findRepositoryAndAccount(
    knownMappings: Set<M>,
    repository: GitRepository,
    remote: GitRemote,
    accounts: Collection<A>,
    savedAccount: A?,
    defaultAccount: A?,
  ): Pair<M, A>? where M : HostedGitRepositoryMapping,
                       M : GitRepositoryMappingData,
                       A : ServerAccount {
    val mappedRepository = knownMappings.find {
      it.gitRepository == repository && it.gitRemote == remote
    } ?: return null

    if (savedAccount != null) {
      if (mappedRepository.matchesAccount(savedAccount)) {
        return mappedRepository to savedAccount
      }
      else {
        return null
      }
    }

    if (defaultAccount != null && mappedRepository.matchesAccount(defaultAccount)) {
      return mappedRepository to defaultAccount
    }

    val singularMatchingAccount = accounts.singleOrNull {
      mappedRepository.matchesAccount(it)
    } ?: return null

    return mappedRepository to singularMatchingAccount
  }
}

@PublishedApi
internal fun HostedGitRepositoryMapping.matchesAccount(account: ServerAccount) =
  URIUtil.equalWithoutSchema(repository.serverPath.toURI(), account.server.toURI())

fun GitPushRepoResult.findRemoteBranch(repository: GitRepository): GitRemoteBranch? =
  repository.branches.findRemoteBranch(GitBranchUtil.stripRefsPrefix(targetBranch))