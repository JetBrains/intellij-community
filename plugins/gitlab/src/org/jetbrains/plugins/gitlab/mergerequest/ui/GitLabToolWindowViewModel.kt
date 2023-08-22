// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowViewModel
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.createSingleProjectAndAccountState
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowProjectViewModel.Companion.GitLabToolWindowProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabRepositoryAndAccountSelectorViewModel
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
      connection?.let { GitLabToolWindowProjectViewModel(project, accountManager, it, this@GitLabToolWindowViewModel) }
    }.stateIn(cs, SharingStarted.Eagerly, null)

  val selectorVm: GitLabRepositoryAndAccountSelectorViewModel = run {
    val preferences = project.service<GitLabMergeRequestsPreferences>()
    GitLabRepositoryAndAccountSelectorViewModel(
      cs, projectsManager, accountManager,
      onSelected = { mapping, account ->
        withContext(cs.coroutineContext) {
          connectionManager.openConnection(mapping, account)
          preferences.selectedRepoAndAccount = mapping to account
        }
      }
    ).apply {
      preferences.selectedRepoAndAccount?.let { (repo, account) ->
        repoSelectionState.value = repo
        accountSelectionState.value = account
        submitSelection()
      }
    }
  }

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
      project.service<GitLabMergeRequestsPreferences>().selectedRepoAndAccount = null
      connectionManager.closeConnection()
    }
  }

  fun activate() {
    _activationRequests.tryEmit(Unit)
  }
}