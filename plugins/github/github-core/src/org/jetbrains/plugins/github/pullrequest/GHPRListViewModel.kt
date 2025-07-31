// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.codereview.list.ReviewListViewModel
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRPersistentInteractionState
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRListPersistentSearchHistory
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRSearchHistoryModel
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRSearchPanelViewModel
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider
import org.jetbrains.plugins.github.util.GithubSettings

@ApiStatus.Experimental
class GHPRListViewModel internal constructor(
  project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
) : ReviewListViewModel {
  private val cs = parentCs.childScope(javaClass.name)

  private val interactionStateService = project.service<GHPRPersistentInteractionState>()
  private val repositoryDataService = dataContext.repositoryDataService
  private val listLoader = dataContext.listLoader
  private val settings = GithubSettings.getInstance()

  val account: GithubAccount = dataContext.securityService.account
  private val currentUser: GHUser = dataContext.securityService.currentUser
  val repository: @NlsSafe String = repositoryDataService.repositoryCoordinates.repositoryPath.repository

  private val outdatedState = MutableStateFlow(false)
  val outdated: StateFlow<Boolean> = outdatedState.asStateFlow()

  val isLoading: StateFlow<Boolean> = listLoader.isLoading
  val error: StateFlow<Throwable?> = listLoader.error

  /**
   * Whether the list view contains any PRs with updates, or `null` if it's unknown (because of the setting).
   */
  private val hasUpdatesState: MutableStateFlow<Boolean?> = MutableStateFlow(false)
  val hasUpdates: StateFlow<Boolean?> = hasUpdatesState.asStateFlow()

  init {
    cs.launchNow {
      interactionStateService.updateSignal.collectLatest {
        checkIsSeenMarkers(listLoader.loadedData.value)
      }
    }

    cs.launchNow {
      listLoader.loadedData.combine(listLoader.isLoading) { l, r -> l to r }.collectLatest { (items, isLoading) ->
        if (!isLoading) {
          checkIsSeenMarkers(items)
        }
      }
    }

    dataContext.listUpdatesChecker.addOutdatedStateChangeListener(cs.nestedDisposable()) {
      outdatedState.value = dataContext.listUpdatesChecker.outdated
    }
  }

  private val searchHistoryModel = GHPRSearchHistoryModel(project.service<GHPRListPersistentSearchHistory>())
  val searchVm: GHPRSearchPanelViewModel = GHPRSearchPanelViewModel(cs, project, repositoryDataService, searchHistoryModel, dataContext.securityService.currentUser)

  private val _focusRequests = Channel<Unit>(1)
  internal val focusRequests: Flow<Unit> = _focusRequests.receiveAsFlow()

  internal val loadedData: StateFlow<List<GHPullRequestShort>> = listLoader.loadedData

  val avatarIconsProvider: GHAvatarIconsProvider = dataContext.avatarIconsProvider

  init {
    cs.launchNow {
      searchVm.searchState.collectLatest {
        listLoader.searchQuery = it.toQuery()
      }
    }
  }

  private fun checkIsSeenMarkers(items: List<GHPullRequestShort>) {
    hasUpdatesState.update {
      if (settings.isSeenMarkersEnabled) {
        items.any { !interactionStateService.isSeen(it, currentUser) }
      }
      else null
    }
  }

  override fun refresh() {
    listLoader.refresh()
    repositoryDataService.resetData()
  }

  override fun reload() {
    listLoader.reload()
    repositoryDataService.resetData()
  }

  fun requestMore() {
    listLoader.tryLoadMore()
  }

  fun requestFocus() {
    _focusRequests.trySend(Unit)
  }
}