// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import git4idea.remote.hosting.HostedGitRepositoryConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

interface GitLabProjectConnectionManager : HostedGitRepositoryConnectionManager<GitLabProjectMapping, GitLabAccount, GitLabProjectConnection>

internal class GitLabProjectConnectionManagerImpl(scope: CoroutineScope,
                                                  repositoriesManager: GitLabProjectsManager,
                                                  accountManager: GitLabAccountManager)
  : GitLabProjectConnectionManager {

  private val connectionRequestsFlow = MutableSharedFlow<Pair<GitLabProjectMapping, GitLabAccount>?>()

  override val state = MutableStateFlow<GitLabProjectConnection?>(null)

  init {
    scope.launch {
      combine(connectionRequestsFlow,
              repositoriesManager.knownRepositoriesState,
              accountManager.accountsState) { request, repositories, accountsMap ->
        val (repo, account) = request ?: return@combine null
        if (!repositories.contains(repo)) return@combine null
        val token = accountsMap[account] ?: return@combine null
        ConnectionData(repo, account, token)
      }.collect {
        state.update { conn ->
          updateConnection(conn, it)
        }
      }
    }
  }

  private fun updateConnection(currentConnection: GitLabProjectConnection?,
                               connectionData: ConnectionData?): GitLabProjectConnection? {
    if (currentConnection != null) {
      if (connectionData == null || currentConnection.repo != connectionData.repo || currentConnection.account != connectionData.account) {
        return null
      }
      currentConnection.token = connectionData.token
      return currentConnection
    }
    else {
      if (connectionData != null) {
        return GitLabProjectConnection(connectionData.repo, connectionData.account, connectionData.token)
      }
      return null
    }
  }

  override suspend fun tryConnect(repo: GitLabProjectMapping, account: GitLabAccount) {
    connectionRequestsFlow.emit(repo to account)
  }

  override suspend fun disconnect() {
    connectionRequestsFlow.emit(null)
  }

  private data class ConnectionData(
    val repo: GitLabProjectMapping,
    val account: GitLabAccount,
    val token: String
  )
}
