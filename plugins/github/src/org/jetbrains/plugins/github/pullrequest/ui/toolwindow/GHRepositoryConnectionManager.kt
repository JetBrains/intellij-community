// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import git4idea.remote.hosting.HostedGitRepositoryConnectionManager
import git4idea.remote.hosting.HostedGitRepositoryConnectionValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

internal class GHRepositoryConnectionManager(scope: CoroutineScope,
                                             private val repositoriesManager: GHHostedRepositoriesManager,
                                             private val accountManager: GHAccountManager,
                                             private val executorManager: GithubApiRequestExecutorManager,
                                             private val settings: GithubPullRequestsProjectUISettings)
  : HostedGitRepositoryConnectionManager<GHGitRepositoryMapping, GithubAccount, GHRepositoryConnection> {

  override val state = MutableStateFlow<GHRepositoryConnection?>(null)

  init {
    scope.launch {
      settings.selectedRepoAndAccount?.let {
        tryConnect(it.first, it.second)
      }
    }

    scope.launch {
      HostedGitRepositoryConnectionValidator.validate(state, repositoriesManager, accountManager)
    }
  }

  override suspend fun tryConnect(repo: GHGitRepositoryMapping, account: GithubAccount) {
    return try {
      val connection = withContext(Dispatchers.IO) {
        GHRepositoryConnection(repo, account, executorManager.getExecutor(account))
      }
      withContext(Dispatchers.Main) {
        settings.selectedRepoAndAccount = repo to account
      }
      state.value = connection
    }
    catch (_: Exception) {
    }
  }

  override suspend fun disconnect() {
    withContext(Dispatchers.Main) {
      settings.selectedRepoAndAccount = null
    }
    state.value = null
  }
}
