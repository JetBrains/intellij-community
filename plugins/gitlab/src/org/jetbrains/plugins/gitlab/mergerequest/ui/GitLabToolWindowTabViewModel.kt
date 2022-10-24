// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.mapStateScoped
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.collaboration.ui.icon.IconsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
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

internal class GitLabToolWindowTabViewModel(scope: CoroutineScope,
                                            private val connectionManager: GitLabProjectConnectionManager,
                                            private val projectsManager: GitLabProjectsManager,
                                            private val accountManager: GitLabAccountManager) {

  val nestedViewModelState: StateFlow<NestedViewModel> = connectionManager.state.mapStateScoped(scope) { scope, connection ->
    if (connection != null) {
      MergeRequests(scope, connection)
    }
    else {
      Selectors(GitLabRepositoryAndAccountSelectorViewModel(scope, connectionManager, projectsManager, accountManager))
    }
  }

  internal sealed interface NestedViewModel {
    class Selectors(val selectorVm: GitLabRepositoryAndAccountSelectorViewModel) : NestedViewModel

    class MergeRequests(scope: CoroutineScope, connection: GitLabProjectConnection) : NestedViewModel {
      private val avatarIconsProvider: IconsProvider<GitLabUserDTO> = CachingIconsProvider(
        AsyncImageIconsProvider(scope, GitLabImageLoader(connection.apiClient, connection.repo.repository.serverPath))
      )

      private val filterVm: GitLabMergeRequestsFiltersViewModel = GitLabMergeRequestsFiltersViewModelImpl(
        scope,
        historyModel = GitLabMergeRequestsFiltersHistoryModel(GitLabMergeRequestsPersistentFiltersHistory()),
        avatarIconsProvider = avatarIconsProvider,
        projectDetailsLoader = GitLabProjectDetailsLoader(connection.apiClient, connection.repo.repository)
      )

      val listVm: GitLabMergeRequestsListViewModel = GitLabMergeRequestsListViewModelImpl(
        scope,
        filterVm = filterVm,
        repository = connection.repo.repository.projectPath.name,
        avatarIconsProvider = avatarIconsProvider,
        loaderSupplier = { filtersValue ->
          GitLabMergeRequestsListLoader(connection.apiClient, connection.repo.repository, filtersValue.toSearchQuery())
        }
      )
    }
  }
}