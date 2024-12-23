// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  private val cs = parentCs.childScope()

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
    cs.launchNow {
      repositoriesManager.knownRepositoriesState.collectLatest { repos ->
        if (connectionManager.connectionState.value != null) {
          return@collectLatest
        }
        val (url, account) = settings.selectedUrlAndAccount ?: return@collectLatest
        val repo = repos.find {
          it.remote.url == url
        } ?: return@collectLatest
        with(vm) {
          repoSelectionState.value = repo
          accountSelectionState.value = account
          submitSelection()
        }
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
      settings.selectedUrlAndAccount = repo.remote.url to account
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
      settings.selectedUrlAndAccount = null
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

  fun loginIfPossible() {
    // init selector, so that auto-login is done automatically
    selectorVm
  }
}