// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapStateScoped
import com.intellij.collaboration.util.URIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

internal class GHPRToolWindowTabViewModel(private val scope: CoroutineScope,
                                          private val repositoriesManager: GHHostedRepositoriesManager,
                                          private val accountManager: GHAccountManager,
                                          private val connectionManager: GHRepositoryConnectionManager,
                                          private val settings: GithubPullRequestsProjectUISettings) {

  private val connectionState = MutableStateFlow<GHRepositoryConnection?>(null).apply {
    scope.launch {
      collectLatest {
        if (it != null) {
          it.awaitClose()
          compareAndSet(it, null)
        }
      }
    }
  }

  private val singleRepoAndAccountState: StateFlow<Pair<GHGitRepositoryMapping, GithubAccount>?> =
    combineState(scope, repositoriesManager.knownRepositoriesState, accountManager.accountsState) { repos, accounts ->
      repos.singleOrNull()?.let { repo ->
        accounts.singleOrNull { URIUtil.equalWithoutSchema(it.server.toURI(), repo.repository.serverPath.toURI()) }?.let {
          repo to it
        }
      }
    }

  val viewState: StateFlow<GHPRTabContentViewModel> = connectionState.mapStateScoped(scope) { scope, connection ->
    if (connection != null) {
      createConnectedVm(connection)
    }
    else {
      createNotConnectedVm(scope)
    }
  }

  private fun createNotConnectedVm(cs: CoroutineScope): GHPRTabContentViewModel.Selectors {
    val selectorVm = GHRepositoryAndAccountSelectorViewModel(cs, repositoriesManager, accountManager, ::connect)

    settings.selectedRepoAndAccount?.let { (repo, account) ->
      with(selectorVm) {
        repoSelectionState.value = repo
        accountSelectionState.value = account
        submitSelection()
      }
    }

    cs.launch {
      singleRepoAndAccountState.collect {
        if (it != null) {
          with(selectorVm) {
            repoSelectionState.value = it.first
            accountSelectionState.value = it.second
            submitSelection()
          }
        }
      }
    }
    return GHPRTabContentViewModel.Selectors(selectorVm)
  }

  private suspend fun connect(repo: GHGitRepositoryMapping, account: GithubAccount) {
    connectionState.value = connectionManager.connect(scope, repo, account)
    settings.selectedRepoAndAccount = repo to account
  }

  private fun createConnectedVm(connection: GHRepositoryConnection) = GHPRTabContentViewModel.PullRequests(connection)

  fun canSelectDifferentRepoOrAccount(): Boolean {
    return viewState.value is GHPRTabContentViewModel.PullRequests && singleRepoAndAccountState.value == null
  }

  fun selectDifferentRepoOrAccount() {
    scope.launch {
      settings.selectedRepoAndAccount = null
      connectionState.value?.close()
    }
  }
}

internal sealed interface GHPRTabContentViewModel {
  class Selectors(val selectorVm: GHRepositoryAndAccountSelectorViewModel) : GHPRTabContentViewModel
  class PullRequests(val connection: GHRepositoryConnection) : GHPRTabContentViewModel
}
