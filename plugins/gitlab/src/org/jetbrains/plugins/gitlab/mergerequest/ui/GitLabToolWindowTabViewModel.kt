// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapStateScoped
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.URIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabMergeRequestsListLoader
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabProjectDetailsLoader
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowTabViewModel.NestedViewModel.MergeRequests
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowTabViewModel.NestedViewModel.Selectors
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersHistoryModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsPersistentFiltersHistory
import org.jetbrains.plugins.gitlab.providers.GitLabImageLoader
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

internal class GitLabToolWindowTabViewModel(private val scope: CoroutineScope,
                                            private val connectionManager: GitLabProjectConnectionManager,
                                            projectsManager: GitLabProjectsManager,
                                            accountManager: GitLabAccountManager) {

  private val connectionState = MutableStateFlow<GitLabProjectConnection?>(null)

  init {
    scope.launch {
      connectionState.collectLatest {
        if (it != null) {
          it.awaitClose()
          connectionState.compareAndSet(it, null)
        }
      }
    }
  }

  private val singleRepoAndAccountState: StateFlow<Pair<GitLabProjectMapping, GitLabAccount>?> =
    combineState(scope, projectsManager.knownRepositoriesState, accountManager.accountsState) { repos, accounts ->
      repos.singleOrNull()?.let { repo ->
        accounts.singleOrNull { URIUtil.equalWithoutSchema(it.server.toURI(), repo.repository.serverPath.toURI()) }?.let {
          repo to it
        }
      }
    }

  val nestedViewModelState: StateFlow<NestedViewModel> = connectionState.mapStateScoped(scope) { scope, connection ->
    if (connection != null) {
      MergeRequests(scope, connection, accountManager)
    }
    else {
      val selectorVm = GitLabRepositoryAndAccountSelectorViewModel(scope, projectsManager, accountManager, ::connect)

      scope.launch {
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

      Selectors(selectorVm)
    }
  }

  private suspend fun connect(project: GitLabProjectMapping, account: GitLabAccount) {
    connectionState.value = connectionManager.connect(scope, project, account)
  }

  internal sealed interface NestedViewModel {
    class Selectors(val selectorVm: GitLabRepositoryAndAccountSelectorViewModel) : NestedViewModel

    class MergeRequests(
      scope: CoroutineScope,
      connection: GitLabProjectConnection,
      accountManager: GitLabAccountManager
    ) : NestedViewModel {
      private val avatarIconsProvider: IconsProvider<GitLabUserDTO> = CachingIconsProvider(
        AsyncImageIconsProvider(scope, GitLabImageLoader(connection.apiClient, connection.repo.repository.serverPath))
      )

      private val filterVm: GitLabMergeRequestsFiltersViewModel = GitLabMergeRequestsFiltersViewModelImpl(
        scope,
        currentUser = connection.currentUser,
        historyModel = GitLabMergeRequestsFiltersHistoryModel(GitLabMergeRequestsPersistentFiltersHistory()),
        avatarIconsProvider = avatarIconsProvider,
        projectDetailsLoader = GitLabProjectDetailsLoader(connection.apiClient, connection.repo.repository)
      )

      val listVm: GitLabMergeRequestsListViewModel = GitLabMergeRequestsListViewModelImpl(
        scope,
        filterVm = filterVm,
        repository = connection.repo.repository.projectPath.name,
        account = connection.account,
        avatarIconsProvider = avatarIconsProvider,
        accountManager = accountManager,
        loaderSupplier = { filtersValue ->
          GitLabMergeRequestsListLoader(connection.apiClient, connection.repo.repository, filtersValue.toSearchQuery())
        }
      )
    }
  }
}