// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.codereview.list.ReviewListViewModel
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.CollectionListModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRPersistentInteractionState
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRListPersistentSearchHistory
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRSearchHistoryModel
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRSearchPanelViewModel
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.cloneDialog.GHCloneDialogExtensionComponentBase.Companion.items
import org.jetbrains.plugins.github.util.GithubSettings
import javax.swing.ListModel

@ApiStatus.Experimental
class GHPRListViewModel internal constructor(
  project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext
) : ReviewListViewModel {
  private val cs = parentCs.childScope()

  private val interactionStateService = project.service<GHPRPersistentInteractionState>()
  private val repositoryDataService = dataContext.repositoryDataService
  private val listLoader = dataContext.listLoader
  private val settings = GithubSettings.getInstance()

  val account: GithubAccount = dataContext.securityService.account
  private val currentUser: GHUser = dataContext.securityService.currentUser
  val repository: @NlsSafe String = repositoryDataService.repositoryCoordinates.repositoryPath.repository

  val listModel: ListModel<GHPullRequestShort> = CollectionListModel(listLoader.loadedData).also { model ->
    listLoader.addDataListener(cs.nestedDisposable(), object : GHListLoader.ListDataListener {
      override fun onDataAdded(startIdx: Int) {
        val loadedData = listLoader.loadedData
        model.add(loadedData.subList(startIdx, loadedData.size))
      }

      override fun onDataUpdated(idx: Int) {
        model.setElementAt(listLoader.loadedData[idx], idx)
      }
      override fun onDataRemoved(idx: Int) {
        model.remove(idx)
      }

      override fun onAllDataRemoved() {
        model.removeAll()
      }
    })
  }

  private val loadingState = MutableStateFlow(false)
  val loading: SharedFlow<Boolean> = loadingState.asSharedFlow()
  private val errorState = MutableStateFlow<Throwable?>(null)
  val error: SharedFlow<Throwable?> = errorState.asSharedFlow()
  private val outdatedState = MutableStateFlow(false)
  val outdated: SharedFlow<Boolean> = outdatedState.asSharedFlow()

  /**
   * Whether the list view contains any PRs with updates, or `null` if it's unknown (because of the setting).
   */
  private val hasUpdatesState: MutableStateFlow<Boolean?> = MutableStateFlow(false)
  val hasUpdates = hasUpdatesState.asSharedFlow()

  init {
    cs.launchNow {
      interactionStateService.updateSignal.collectLatest {
        checkIsSeenMarkers()
      }
    }

    val listenersDisposable = cs.nestedDisposable()
    listLoader.addLoadingStateChangeListener(listenersDisposable) {
      loadingState.value = listLoader.loading

      // If transitioning from loading to not loading, check for no updates
      if (!listLoader.loading) {
        checkIsSeenMarkers()
      }
    }
    listLoader.addErrorChangeListener(listenersDisposable) {
      errorState.value = listLoader.error
    }
    dataContext.listUpdatesChecker.addOutdatedStateChangeListener(listenersDisposable) {
      outdatedState.value = dataContext.listUpdatesChecker.outdated
    }

    listLoader.addDataListener(listenersDisposable, object : GHListLoader.ListDataListener {
      override fun onAllDataRemoved() {
        listLoader.loadMore()
      }
    })
  }

  private val searchHistoryModel = GHPRSearchHistoryModel(project.service<GHPRListPersistentSearchHistory>())
  val searchVm = GHPRSearchPanelViewModel(cs, project, repositoryDataService, searchHistoryModel, dataContext.securityService.currentUser)

  private val _focusRequests = Channel<Unit>(1)
  internal val focusRequests: Flow<Unit> = _focusRequests.receiveAsFlow()

  val avatarIconsProvider: GHAvatarIconsProvider = dataContext.avatarIconsProvider

  init {
    cs.launchNow {
      searchVm.searchState.collectLatest {
        listLoader.searchQuery = it.toQuery()
      }
    }
  }

  private fun checkIsSeenMarkers() {
    hasUpdatesState.update {
      if (settings.isSeenMarkersEnabled) {
        listModel.items.any { !interactionStateService.isSeen(it, currentUser) }
      }
      else null
    }
  }

  override fun refresh() {
    listLoader.reset()
    repositoryDataService.resetData()
  }

  fun requestMore() {
    if (listLoader.canLoadMore()) {
      listLoader.loadMore()
    }
  }

  fun requestFocus() {
    _focusRequests.trySend(Unit)
  }
}