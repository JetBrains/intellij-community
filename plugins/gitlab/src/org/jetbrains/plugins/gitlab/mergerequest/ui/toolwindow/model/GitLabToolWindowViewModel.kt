// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowViewModel
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.createSingleProjectAndAccountState
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowProjectViewModel.Companion.GitLabToolWindowProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestsUtil.repoAndAccountState
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

@Service(Service.Level.PROJECT)
internal class GitLabToolWindowViewModel(
  private val project: Project,
  parentCs: CoroutineScope
) : ReviewToolwindowViewModel<GitLabToolWindowProjectViewModel> {
  private val cs = parentCs.childScope(Dispatchers.Default)

  private val connectionManager: GitLabProjectConnectionManager = project.service<GitLabProjectConnectionManager>()
  private val projectsManager: GitLabProjectsManager = project.service<GitLabProjectsManager>()
  private val accountManager: GitLabAccountManager = service<GitLabAccountManager>()

  val isAvailable: StateFlow<Boolean> = projectsManager.knownRepositoriesState.mapState(cs) {
    it.isNotEmpty()
  }

  override val projectVm: StateFlow<GitLabToolWindowProjectViewModel?> =
    connectionManager.connectionState.mapScoped { connection ->
      connection?.let { GitLabToolWindowProjectViewModel(project, accountManager, projectsManager, it, this@GitLabToolWindowViewModel) }
    }.stateIn(cs, SharingStarted.Eagerly, null)

  val selectorVm: StateFlow<GitLabRepositoryAndAccountSelectorViewModel?> = isAvailable.mapScoped {
    val preferences = project.service<GitLabMergeRequestsPreferences>()
    GitLabRepositoryAndAccountSelectorViewModel(
      this, projectsManager, accountManager,
      onSelected = { mapping, account ->
        connectionManager.openConnection(mapping, account)
        preferences.selectedUrlAndAccountId = mapping.remote.url to account.id
      }
    ).apply {
      // Make sure the first found selected repo and account will be selected
      launch {
        val (repo, account) =
          repoAndAccountState(
            projectsManager.knownRepositoriesState,
            accountManager.accountsState,
            preferences.selectedUrlAndAccountId ?: return@launch
          ).filterNotNull().first()

        selectRepoAndAccount(repo, account)
        submitSelection()
      }
    }
  }.stateIn(cs, SharingStarted.Eagerly, null)

  private val _activationRequests = MutableSharedFlow<Unit>(1)
  val activationRequests: Flow<Unit> = _activationRequests.asSharedFlow()

  private val singleProjectAndAccountState: StateFlow<Pair<GitLabProjectMapping, GitLabAccount>?> =
    createSingleProjectAndAccountState(cs, projectsManager, accountManager)

  val canSwitchProject: StateFlow<Boolean> =
    combineState(cs, projectVm, singleProjectAndAccountState) { currentProjectContext, currentSingleRepoAndAccountState ->
      // project can be switched when any project is selected and there are no 1-1 mapping with project and account
      currentProjectContext != null && currentSingleRepoAndAccountState == null
    }

  fun switchProject() {
    cs.launch {
      project.service<GitLabMergeRequestsPreferences>().selectedUrlAndAccountId = null
      connectionManager.closeConnection()
    }
  }

  /**
   * Awaits the initialization and tries to log in if there's a fitting repo and account
   * Will do nothing if auto-login is disabled via registry
   */
  internal suspend fun loginIfPossible() {
    if (!Registry.`is`("vcs.gitlab.connect.silently", true)) return
    selectorVm.first()?.submitSelection()
  }

  fun activate() {
    _activationRequests.tryEmit(Unit)
  }

  fun activateAndAwaitProject(action: GitLabToolWindowProjectViewModel.() -> Unit) {
    cs.launch {
      _activationRequests.emit(Unit)
      projectVm.filterNotNull().first().action()
    }
  }
}