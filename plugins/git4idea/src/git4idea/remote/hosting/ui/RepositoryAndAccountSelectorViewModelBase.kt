// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.ui

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.util.URIUtil
import com.intellij.util.childScope
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.HostedGitRepositoryMapping
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

abstract class RepositoryAndAccountSelectorViewModelBase<M : HostedGitRepositoryMapping, A : ServerAccount>(
  parentCs: CoroutineScope,
  repositoriesManager: HostedGitRepositoriesManager<M>,
  accountManager: AccountManager<A, *>,
  private val onSelected: suspend (M, A) -> Unit
) : RepositoryAndAccountSelectorViewModel<M, A> {

  private val cs = parentCs.childScope()

  final override val repositoriesState = repositoriesManager.knownRepositoriesState

  final override val repoSelectionState = MutableStateFlow<M?>(null)

  final override val accountsState = combineState(cs, accountManager.accountsState, repoSelectionState) { accounts, repo ->
    if (repo == null) {
      emptyList()
    }
    else {
      accounts.filter { URIUtil.equalWithoutSchema(it.server.toURI(), repo.repository.serverPath.toURI()) }
    }
  }

  final override val accountSelectionState = MutableStateFlow<A?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  final override val missingCredentialsState: StateFlow<Boolean> =
    accountSelectionState.transformLatest {
      if(it == null) {
        emit(false)
      } else {
        coroutineScope {
          accountManager.getCredentialsState(this, it).collect { creds ->
            emit(creds == null)
          }
        }
      }
    }.stateIn(cs, SharingStarted.Eagerly, false)

  final override val submitAvailableState: StateFlow<Boolean> =
    combine(repoSelectionState, accountSelectionState, missingCredentialsState) { repo, acc, credsMissing ->
      repo != null && acc != null && !credsMissing
    }.stateIn(cs, SharingStarted.Eagerly, false)

  override fun submitSelection() {
    val repo = repoSelectionState.value ?: return
    val account = accountSelectionState.value ?: return
    cs.launch {
      onSelected(repo, account)
    }
  }
}