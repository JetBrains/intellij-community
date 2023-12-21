// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowViewModel
import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.ui.selector.GHRepositoryAndAccountSelectorViewModel
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
class GHPRToolWindowViewModel internal constructor(private val project: Project, parentCs: CoroutineScope)
  : ReviewToolwindowViewModel<GHPRToolWindowProjectViewModel> {

  private val accountManager: GHAccountManager get() = service()
  private val repositoriesManager: GHHostedRepositoriesManager get() = project.service()
  private val connectionManager: GHRepositoryConnectionManager get() = project.service()
  private val settings: GithubPullRequestsProjectUISettings
    get() = GithubPullRequestsProjectUISettings.getInstance(project)

  //TODO: switch to Default dispatcher
  private val cs = parentCs.childScope(Dispatchers.Main)

  val isAvailable: StateFlow<Boolean> = repositoriesManager.knownRepositoriesState.mapState(cs) {
    it.isNotEmpty()
  }

  private val _activationRequests = MutableSharedFlow<Unit>(1)
  internal val activationRequests: Flow<Unit> = _activationRequests.asSharedFlow()

  private val singleRepoAndAccountState: StateFlow<Pair<GHGitRepositoryMapping, GithubAccount>?> =
    combineState(cs, repositoriesManager.knownRepositoriesState, accountManager.accountsState) { repos, accounts ->
      repos.singleOrNull()?.let { repo ->
        accounts.singleOrNull { URIUtil.equalWithoutSchema(it.server.toURI(), repo.repository.serverPath.toURI()) }?.let {
          repo to it
        }
      }
    }

  val selectorVm: GHRepositoryAndAccountSelectorViewModel by lazy {
    val vm = GHRepositoryAndAccountSelectorViewModel(cs, repositoriesManager, accountManager, ::connect)
    settings.selectedRepoAndAccount?.let { (repo, account) ->
      with(vm) {
        repoSelectionState.value = repo
        accountSelectionState.value = account
        submitSelection()
      }
    }

    //TODO: only do when UI is showing
    cs.launchNow {
      singleRepoAndAccountState.collect {
        if (it != null) {
          with(vm) {
            repoSelectionState.value = it.first
            accountSelectionState.value = it.second
            submitSelection()
          }
        }
      }
    }
    vm
  }

  private suspend fun connect(repo: GHGitRepositoryMapping, account: GithubAccount) {
    withContext(cs.coroutineContext) {
      connectionManager.openConnection(repo, account)
      settings.selectedRepoAndAccount = repo to account
    }
  }

  override val projectVm: StateFlow<GHPRToolWindowProjectViewModel?> by lazy {
    project.service<GHRepositoryConnectionManager>().connectionState
      .mapScoped { ctx -> ctx?.let { GHPRToolWindowProjectViewModel(project, this, this@GHPRToolWindowViewModel, it) } }
      .stateIn(cs, SharingStarted.Lazily, null)
  }

  fun canResetRemoteOrAccount(): Boolean = connectionManager.connectionState.value != null && singleRepoAndAccountState.value == null

  fun resetRemoteAndAccount() {
    cs.launch {
      settings.selectedRepoAndAccount = null
      connectionManager.closeConnection()
    }
  }

  fun activate() {
    _activationRequests.tryEmit(Unit)
  }

  fun activateAndAwaitProject(action: GHPRToolWindowProjectViewModel.() -> Unit) {
    cs.launch {
      _activationRequests.emit(Unit)
      projectVm.filterNotNull().first().action()
    }
  }
}