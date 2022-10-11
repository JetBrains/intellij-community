// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.ui

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.util.URIUtil
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.HostedGitRepositoryMapping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

abstract class RepositoryAndAccountSelectorViewModelBase<M : HostedGitRepositoryMapping, A : ServerAccount>(
  scope: CoroutineScope,
  repositoriesManager: HostedGitRepositoriesManager<M>,
  accountManager: AccountManager<A, *>
) : RepositoryAndAccountSelectorViewModel<M, A> {

  final override val repositoriesState = repositoriesManager.knownRepositoriesState

  final override val repoSelectionState = MutableStateFlow<M?>(null)

  final override val accountsState = combineState(scope, accountManager.accountsState, repoSelectionState) { accounts, repo ->
    if (repo == null) {
      emptyList()
    }
    else {
      accounts.filter { URIUtil.equalWithoutSchema(it.server.toURI(), repo.repository.serverPath.toURI()) }
    }
  }

  final override val accountSelectionState = MutableStateFlow<A?>(null)

  final override val missingCredentialsState: StateFlow<Boolean> =
    channelFlow {
      accountSelectionState.collectLatest {
        if(it == null) {
          send(false)
        } else {
          accountManager.getCredentialsFlow(it, true).collect { creds ->
            send(creds == null)
          }
        }
      }
    }.stateIn(scope, SharingStarted.Eagerly, false)

  final override val submitAvailableState: StateFlow<Boolean> =
    combine(repoSelectionState, accountSelectionState, missingCredentialsState) { repo, acc, credsMissing ->
      repo != null && acc != null && !credsMissing
    }.stateIn(scope, SharingStarted.Eagerly, false)
}