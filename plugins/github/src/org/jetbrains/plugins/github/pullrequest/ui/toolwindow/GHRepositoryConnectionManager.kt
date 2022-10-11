// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import git4idea.remote.hosting.HostedGitRepositoryConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

internal class GHRepositoryConnectionManager(scope: CoroutineScope,
                                             private val repositoriesManager: GHHostedRepositoriesManager,
                                             private val accountManager: GHAccountManager,
                                             private val settings: GithubPullRequestsProjectUISettings)
  : HostedGitRepositoryConnectionManager<GHGitRepositoryMapping, GithubAccount, GHRepositoryConnection> {

  private val connectionRequestsFlow = MutableSharedFlow<Pair<GHGitRepositoryMapping, GithubAccount>?>()

  override val state by lazy {
    connectionRequestsFlow
      .handleConnectionRequest()
      .stateIn(scope, SharingStarted.Eagerly, null)
  }

  private fun Flow<Pair<GHGitRepositoryMapping, GithubAccount>?>.handleConnectionRequest(): Flow<GHRepositoryConnection?> =
    channelFlow {
      var connection: GHRepositoryConnection? = null
      distinctUntilChanged().collectLatest { request ->
        try {
          val (repo, account) = request ?: throw CancellationException()
          combine(repositoriesManager.knownRepositoriesState, accountManager.accountsState) { repositories, accounts ->
            if (!repositories.contains(repo)) {
              throw CancellationException()
            }
            if (!accounts.contains(account)) {
              throw CancellationException()
            }
          }.collectLatest {
            coroutineScope {
              accountManager.getCredentialsFlow(account).collectLatest { token ->
                if (token == null) {
                  throw CancellationException()
                }
                else {
                  val currentConnection = connection
                  if (currentConnection != null && currentConnection.repo == repo && currentConnection.account == account) {
                    currentConnection.token = token
                  }
                  else {
                    connection = GHRepositoryConnection(repo, account, token)
                  }
                  send(connection)
                  withContext(Dispatchers.Main) {
                    settings.selectedRepoAndAccount = repo to account
                  }
                }
              }
            }
          }
        }
        catch (ce: Exception) {
          connection = null
          send(null)
        }
      }
    }

  init {
    scope.launch {
      settings.selectedRepoAndAccount?.let {
        tryConnect(it.first, it.second)
      }
    }
  }

  override suspend fun tryConnect(repo: GHGitRepositoryMapping, account: GithubAccount) {
    connectionRequestsFlow.emit(repo to account)
  }

  override suspend fun disconnect() {
    withContext(Dispatchers.Main) {
      settings.selectedRepoAndAccount = null
    }
    connectionRequestsFlow.emit(null)
  }
}
