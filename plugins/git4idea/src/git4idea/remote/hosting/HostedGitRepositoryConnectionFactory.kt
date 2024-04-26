// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface HostedGitRepositoryConnectionFactory<M : HostedGitRepositoryMapping, A : ServerAccount, C : HostedGitRepositoryConnection<M, A>> {
  suspend fun connect(parentScope: CoroutineScope, repo: M, account: A): C
}

/**
 * Creates a self-validating connection which is automatically closed when repo, account ort credentials are missing
 */
class ValidatingHostedGitRepositoryConnectionFactory<
  M : HostedGitRepositoryMapping,
  A : ServerAccount,
  C : HostedGitRepositoryConnection<M, A>,
  Cred : Any
  >(
  private val repositoriesManager: () -> HostedGitRepositoriesManager<M>,
  private val accountManager: () -> AccountManager<A, Cred>,
  private val createConnection: suspend CoroutineScope.(M, A, StateFlow<Cred>) -> C)
  : HostedGitRepositoryConnectionFactory<M, A, C> {

  override suspend fun connect(parentScope: CoroutineScope, repo: M, account: A): C {
    val loggingExceptionHandler = CoroutineExceptionHandler { _, e ->
      logger<ValidatingHostedGitRepositoryConnectionFactory<*, *, *, *>>().info(e.localizedMessage)
    }

    // not a supervisor so that if any of the listeners or loaders fail the scope is cancelled
    val connectionScope = parentScope.childScope(loggingExceptionHandler, supervisor = false)

    val credentialsState = accountManager().getCredentialsState(connectionScope, account)
    val requiredCredentialsState = credentialsState
      .transform {
        if (it == null) {
          throw IllegalStateException("Token for account $account is missing")
        }
        else {
          this.emit(it)
        }
      }.stateIn(connectionScope)

    connectionScope.launch {
      repositoriesManager().knownRepositoriesState.collect {
        if (!it.contains(repo)) {
          throw IllegalStateException("Repository mapping $repo is missing")
        }
      }
    }

    connectionScope.launch {
      accountManager().accountsState.collect {
        if (!it.contains(account)) {
          throw IllegalStateException("Account $account is missing")
        }
      }
    }

    return withContext(connectionScope.coroutineContext) {
      createConnection(connectionScope, repo, account, requiredCredentialsState)
    }
  }
}