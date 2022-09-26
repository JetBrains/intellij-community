// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Validates the supplied connection state when repository or accounts lists change and disconnects when connection data is no longer valid
 */
object HostedGitRepositoryConnectionValidator {
  suspend fun <M, A, C> validate(connectionState: MutableStateFlow<C?>,
                                 repositoryManager: HostedGitRepositoriesManager<M>,
                                 accountManager: AccountManager<A, *>)
    where M : HostedGitRepositoryMapping, A : Account, C : HostedGitRepositoryConnection<M, A> =
    coroutineScope {
      launch {
        connectionState.combine(repositoryManager.knownRepositoriesState) { conn, repos ->
          conn to repos
        }.collectLatest { (conn, repos) ->
          connectionState.compareAndSet(conn, checkRepositoryExists(conn, repos))
        }
      }

      launch {
        connectionState.combine(accountManager.accountsState) { conn, accountsMap ->
          conn to accountsMap
        }.collectLatest { (conn, accountsMap) ->
          connectionState.compareAndSet(conn, checkAccountAndCreds(conn, accountsMap))
        }
      }
    }

  private fun <M, C> checkRepositoryExists(connection: C?, repos: Set<M>): C?
    where M : HostedGitRepositoryMapping, C : HostedGitRepositoryConnection<M, *> {
    if (connection == null) return null
    if (!repos.contains(connection.repo)) return null
    return connection
  }

  private fun <A, C> checkAccountAndCreds(connection: C?, accounts: Map<A, Any?>): C?
    where A : Account, C : HostedGitRepositoryConnection<*, A> {
    if (connection == null) return null

    val credentials = accounts[connection.account]
    if (credentials == null) {
      return null
    }
    return connection
  }
}
