// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.util.URIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Automatically connects to the repository if there's only one repository and matching account
 */
class HostedGitRepositoryAutoConnector<M : HostedGitRepositoryMapping, A : ServerAccount, C : HostedGitRepositoryConnection<M, A>>(
  scope: CoroutineScope,
  private val connectionManager: HostedGitRepositoryConnectionManager<M, A, C>,
  repositoryManager: HostedGitRepositoriesManager<M>,
  accountManager: AccountManager<A, *>
) {

  private val singleRepoAndAccountState: StateFlow<Pair<M, A>?> =
    combineState(scope, repositoryManager.knownRepositoriesState, accountManager.accountsState) { repos, accounts ->
      repos.singleOrNull()?.let { repo ->
        accounts.singleOrNull { URIUtil.equalWithoutSchema(it.server.toURI(), repo.repository.serverPath.toURI()) }?.let {
          repo to it
        }
      }
    }

  val canAutoConnectState: StateFlow<Boolean> = singleRepoAndAccountState.mapState(scope) { it != null }

  init {
    scope.launch {
      connectionManager.state.combine(singleRepoAndAccountState) { conn, singles ->
        if (conn == null) singles else null
      }.collect {
        if (it != null) {
          connectionManager.tryConnect(it.first, it.second)
        }
      }
    }
  }
}
