// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.account

import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.auth.findAccountOrNull
import git4idea.GitUtil
import git4idea.push.GitPushRepoResult
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.HostedGitRepositoryMapping
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitRepositoryMappingData

@Deprecated("Use git4idea.push.GitPushNotificationUtil instead")
object AccountUtil {

  /**
   * Select a persisted repository and account
   *
   * @return The selected repository and account pair, or null if no match was found
   */
  fun <M : GitRepositoryMappingData, A : ServerAccount> selectPersistedRepoAndAccount(
    targetRepository: GitRepository,
    pushResult: GitPushRepoResult,
    selectedRepoAndAccount: Pair<M, A>?
  ): RepoAndAccount<M, A>? {
    val (projectMapping, account) = selectedRepoAndAccount ?: return null
    if (targetRepository != projectMapping.gitRepository) return null

    val remote = GitUtil.findRemoteByName(targetRepository, pushResult.targetRemote) ?: return null
    if (remote != projectMapping.gitRemote) return null

    return RepoAndAccount(projectMapping, account)
  }

  /**
   * Select an account from an account list with necessary repository mappings
   *
   * @return  Repository containing the mapping and selected account, or null if no suitable account is found
   */
  fun <M, A : ServerAccount> selectSingleAccount(
    projectsManager: HostedGitRepositoriesManager<M>,
    accountManager: AccountManager<A, *>,
    targetRepository: GitRepository,
    pushResult: GitPushRepoResult,
    defaultAccount: A?
  ): RepoAndAccount<M, A>?
    where M : GitRepositoryMappingData,
          M : HostedGitRepositoryMapping {
    val targetRemote = GitUtil.findRemoteByName(targetRepository, pushResult.targetRemote)
    val projectMapping = projectsManager.knownRepositoriesState.value.find { mapping ->
      mapping.gitRepository == targetRepository && mapping.gitRemote == targetRemote
    } ?: return null

    if (defaultAccount?.server == projectMapping.repository.serverPath) {
      return RepoAndAccount(projectMapping, defaultAccount)
    }

    val account = accountManager.findAccountOrNull { account ->
      account.server == projectMapping.repository.serverPath
    } ?: return null

    return RepoAndAccount(projectMapping, account)
  }
}

@Deprecated("Use git4idea.push.GitPushNotificationUtil instead")
data class RepoAndAccount<M : GitRepositoryMappingData, A : ServerAccount>(
  val projectMapping: M,
  val account: A
)