// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.ui

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.util.URIUtil
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.HostedGitRepositoryMapping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

abstract class RepositoryAndAccountSelectorViewModelBase<M : HostedGitRepositoryMapping, A : ServerAccount>(
  scope: CoroutineScope,
  repositoriesManager: HostedGitRepositoriesManager<M>,
  accountManager: AccountManager<A, *>
) : RepositoryAndAccountSelectorViewModel<M, A> {

  final override val repositoriesState = repositoriesManager.knownRepositoriesState

  final override val repoSelectionState = MutableStateFlow<M?>(null)

  final override val accountsState = combineState(scope, accountManager.accountsState, repoSelectionState) { accountsMap, repo ->
    if (repo == null) {
      emptyList()
    }
    else {
      accountsMap.keys.filter { URIUtil.equalWithoutSchema(it.server.toURI(), repo.repository.serverPath.toURI()) }
    }
  }

  final override val accountSelectionState = MutableStateFlow<A?>(null)

  final override val missingCredentialsState: StateFlow<Boolean> =
    combineState(scope, accountManager.accountsState, accountSelectionState) { accountsMap, account ->
      account != null && accountsMap[account] == null
    }

  final override val submitAvailableState: StateFlow<Boolean> =
    combineState(scope, repoSelectionState, accountSelectionState, missingCredentialsState) { repo, acc, credsMissing ->
      repo != null && acc != null && !credsMissing
    }
}