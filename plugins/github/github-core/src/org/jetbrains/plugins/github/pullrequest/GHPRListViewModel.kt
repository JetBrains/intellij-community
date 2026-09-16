// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.list.ReviewListViewModel
import com.intellij.collaboration.util.AsyncIncrementalListComputer
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
@OptIn(ExperimentalCoroutinesApi::class)
class GHPRListViewModel internal constructor(
  project: Project,
  parentCs: CoroutineScope,
  dataContext: GHPRDataContext,
) : ReviewListViewModel {
  private val cs = parentCs.childScope(javaClass.name)

  private val interactionStateService = project.service<GHPRPersistentInteractionState>()
  private val repositoryDataService = dataContext.repositoryDataService
  private val settings = GithubSettings.getInstance()

  val account: GithubAccount = dataContext.securityService.account
  private val currentUser: GHUser = dataContext.securityService.currentUser
  val repository: @NlsSafe String = repositoryDataService.repositoryCoordinates.repositoryPath.repository

  private val _reloadSignal = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val reloadSignal: SharedFlow<Unit> = _reloadSignal.asSharedFlow()

  private val searchHistoryModel = GHPRSearchHistoryModel(project.service<GHPRListPersistentSearchHistory>())
  val searchVm: GHPRSearchPanelViewModel =
    GHPRSearchPanelViewModel(cs, project, repositoryDataService, dataContext.securityService, searchHistoryModel)
  private val loaderState: StateFlow<AsyncIncrementalListComputer<GHPullRequestShort>?> =
    searchVm.searchState
      .map { search -> dataContext.getListLoader(search.toQuery()) }
      .flatMapLatest { sequence ->
        reloadSignal.withInitial(Unit).mapScoped(true) {
          AsyncIncrementalListComputer.createIn(this, sequence).apply {
            requestMore()
          }
        }
      }
      .stateIn(cs, SharingStarted.Eagerly, null)

  private val loaderStateFlow: Flow<IncrementallyComputedValue<List<GHPullRequestShort>>> =
    loaderState.flatMapLatest { it?.state ?: flowOf(IncrementallyComputedValue.initial()) }

  internal val loadedData: StateFlow<List<GHPullRequestShort>> =
    loaderStateFlow.map { it.valueOrNull ?: emptyList() }
      .stateIn(cs, SharingStarted.Eagerly, emptyList())
  val isLoading: StateFlow<Boolean> =
    loaderStateFlow.map { it.isLoading }
      .stateIn(cs, SharingStarted.Eagerly, false)
  val error: StateFlow<Throwable?> =
    loaderStateFlow.map { it.exceptionOrNull }
      .stateIn(cs, SharingStarted.Eagerly, null)

  /**
   * Whether the list view contains any PRs with updates, or `null` if it's unknown (because of the setting).
   */
  private val hasUpdatesState: MutableStateFlow<Boolean?> = MutableStateFlow(false)
  val hasUpdates: StateFlow<Boolean?> = hasUpdatesState.asStateFlow()

  init {
    cs.launchNow {
      interactionStateService.updateSignal.collectLatest {
        checkIsSeenMarkers(loadedData.value)
      }
    }

    cs.launchNow {
      loadedData.combine(isLoading) { l, r -> l to r }.collectLatest { (items, isLoading) ->
        if (!isLoading) {
          checkIsSeenMarkers(items)
        }
      }
    }
  }

  private val _focusRequests = Channel<Unit>(1)
  internal val focusRequests: Flow<Unit> = _focusRequests.receiveAsFlow()

  val avatarIconsProvider: GHAvatarIconsProvider = dataContext.avatarIconsProvider

  private fun checkIsSeenMarkers(items: List<GHPullRequestShort>) {
    hasUpdatesState.update {
      if (settings.isSeenMarkersEnabled) {
        items.any { !interactionStateService.isSeen(it, currentUser) }
      }
      else null
    }
  }

  override fun refresh() {
    _reloadSignal.tryEmit(Unit)
    repositoryDataService.resetData()
  }

  fun requestMore() {
    loaderState.value?.requestMore()
  }

  fun requestFocus() {
    _focusRequests.trySend(Unit)
  }
}