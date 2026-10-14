// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.ui.selector.GHRepositoryAndAccountSelectorViewModel
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GHPRProjectViewModel(private val project: Project, parentCs: CoroutineScope) {
  private val accountManager: GHAccountManager get() = service()
  private val repositoriesManager: GHHostedRepositoriesManager get() = project.service()
  private val connectionManager: GHRepositoryConnectionManager get() = project.service()
  private val settings: GithubPullRequestsProjectUISettings
    get() = GithubPullRequestsProjectUISettings.getInstance(project)
  private val PREFERRED_REPOSITORY_MAPPING_TIMEOUT: Duration = 1.minutes

  private val cs = parentCs.childScope(this::class)

  val isAvailable: StateFlow<Boolean> = repositoriesManager.knownRepositoriesState.mapState(cs) {
    it.isNotEmpty()
  }

  private val _activationRequests = MutableSharedFlow<Unit>(1)
  @ApiStatus.Internal
  val activationRequests: Flow<Unit> = _activationRequests.asSharedFlow()

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
        // Skip once connected, else a later re-emission would reconnect and cancel in-flight work.
        if (it != null && connectionManager.connectionState.value == null) {
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

  private suspend fun connect(repo: GHGitRepositoryMapping, account: GithubAccount): GHRepositoryConnection? =
    withContext(cs.coroutineContext) {
      // Compare by repository coordinates, not mapping equality: the same repo can re-resolve to a
      // structurally different mapping (e.g. after git sync), which would otherwise trigger a needless
      // reconnect that cancels in-flight work (e.g. a diff load).
      val current = connectionManager.connectionState.value
      if (current != null && current.repo.repository == repo.repository && current.account == account) {
        return@withContext current
      }
      val connection = connectionManager.openConnection(repo, account)
      settings.selectedUrlAndAccount = repo.remote.url to account
      connection
    }

  private val vmFactory = project.service<GHPRConnectedProjectViewModelFactory>()

  val connectedProjectVm: StateFlow<GHPRConnectedProjectViewModel?> by lazy {
    project.service<GHRepositoryConnectionManager>().connectionState
      .mapScoped { ctx -> ctx?.let { vmFactory.create(project, this, it, ::activate) } }
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

  /**
   * @param preferredRepoAndAccount connects directly to this repository and account.
   * The [selectorVm] heuristics need exactly one known repository and account, and a new pull request worktree can have more.
   * [action] always runs against the view model for [preferredRepoAndAccount].
   * When no mapping for the repository appears within [mappingTimeout], [action] does not run.
   * When the connection closes or changes before its view model appears, [action] does not run.
   */
  fun activateAndAwaitProject(
    preferredRepoAndAccount: Pair<GHRepositoryCoordinates, GithubAccount>? = null,
    mappingTimeout: Duration = PREFERRED_REPOSITORY_MAPPING_TIMEOUT,
    action: GHPRConnectedProjectViewModel.() -> Unit,
  ) {
    doActivateAndAwaitProject(preferredRepoAndAccount, mappingTimeout, action)
  }

  /**
   * Does the same as [activateAndAwaitProject] and returns the job of the activation.
   */
  @VisibleForTesting
  internal fun doActivateAndAwaitProject(
    preferredRepoAndAccount: Pair<GHRepositoryCoordinates, GithubAccount>? = null,
    mappingTimeout: Duration = PREFERRED_REPOSITORY_MAPPING_TIMEOUT,
    action: GHPRConnectedProjectViewModel.() -> Unit,
  ): Job {
    return cs.launch {
      _activationRequests.emit(Unit)
      if (preferredRepoAndAccount == null) {
        connectedProjectVm.filterNotNull().first().action()
        return@launch
      }

      val (expectedRepository, account) = preferredRepoAndAccount
      val mapping = withTimeoutOrNull(mappingTimeout) {
        repositoriesManager.knownRepositoriesState
          .mapNotNull { repos -> repos.find { it.repository == expectedRepository } }
          .first()
      }
      if (mapping == null) {
        LOG.warn("No Git remote for GitHub repository $expectedRepository appeared in $mappingTimeout, skipping the requested action")
        return@launch
      }
      val connection = connect(mapping, account) ?: return@launch
      val (currentConnection, vm) = combine(connectionManager.connectionState, connectedProjectVm, ::Pair)
        .first { (currentConnection, vm) -> currentConnection !== connection || vm?.connectionId == connection.id }
      if (currentConnection === connection) {
        vm?.action()
      }
    }
  }

  fun loginIfPossible() {
    // init selector, so that auto-login is done automatically
    selectorVm
  }

  companion object {
    private val LOG = logger<GHPRProjectViewModel>()
  }
}
