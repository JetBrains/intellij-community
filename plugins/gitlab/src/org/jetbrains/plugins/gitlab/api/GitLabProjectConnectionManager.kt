// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import git4idea.remote.hosting.HostedGitRepositoryConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

interface GitLabProjectConnectionManager : HostedGitRepositoryConnectionManager<GitLabProjectMapping, GitLabAccount, GitLabProjectConnection>

internal class GitLabProjectConnectionManagerImpl(scope: CoroutineScope,
                                                  private val repositoriesManager: GitLabProjectsManager,
                                                  private val accountManager: GitLabAccountManager)
  : GitLabProjectConnectionManager {

  private val connectionRequestsFlow = MutableSharedFlow<Pair<GitLabProjectMapping, GitLabAccount>?>()

  override val state by lazy {
    connectionRequestsFlow
      .handleConnectionRequest()
      .stateIn(scope, SharingStarted.Eagerly, null)
  }

  private fun Flow<Pair<GitLabProjectMapping, GitLabAccount>?>.handleConnectionRequest(): Flow<GitLabProjectConnection?> =
    channelFlow {
      var connection: GitLabProjectConnection? = null
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
              accountManager.getCredentialsState(this, account).collectLatest { token ->
                if (token == null) {
                  throw CancellationException()
                }
                else {
                  val currentConnection = connection
                  if (currentConnection != null && currentConnection.repo == repo && currentConnection.account == account) {
                    currentConnection.token = token
                  }
                  else {
                    connection = GitLabProjectConnection(repo, account, token)
                  }
                  send(connection)
                }
              }
            }
          }
        }
        catch (ce: Exception) {
          connection = null
        }
      }
    }

  override suspend fun tryConnect(repo: GitLabProjectMapping, account: GitLabAccount) {
    connectionRequestsFlow.emit(repo to account)
  }

  override suspend fun disconnect() {
    connectionRequestsFlow.emit(null)
  }
}
