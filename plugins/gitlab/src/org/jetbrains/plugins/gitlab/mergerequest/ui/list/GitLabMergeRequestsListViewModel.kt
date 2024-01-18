// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.async.ReloadablePotentiallyInfiniteListLoader
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.ui.codereview.list.ReviewListViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModel

internal interface GitLabMergeRequestsListViewModel : ReviewListViewModel {
  val filterVm: GitLabMergeRequestsFiltersViewModel
  val avatarIconsProvider: IconsProvider<GitLabUserDTO>

  val repository: String

  val listDataFlow: Flow<List<GitLabMergeRequestDetails>>

  val loading: Flow<Boolean>
  val error: Flow<Throwable?>

  fun requestMore()

  override fun refresh()
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestsListViewModelImpl(
  parentCs: CoroutineScope,
  override val filterVm: GitLabMergeRequestsFiltersViewModel,
  override val repository: String,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  private val tokenRefreshFlow: Flow<Unit>,
  private val loaderSupplier: (CoroutineScope, GitLabMergeRequestsFiltersValue) -> ReloadablePotentiallyInfiniteListLoader<GitLabMergeRequestShortRestDTO>
) : GitLabMergeRequestsListViewModel {

  private val scope = parentCs.childScope()

  private val loaderInitFlow = MutableSharedFlow<Unit>(1)

  private val loaderFlow: Flow<ReloadablePotentiallyInfiniteListLoader<GitLabMergeRequestShortRestDTO>> =
    filterVm.searchState.mapScoped { search ->
      loaderSupplier(this, search)
    }.shareIn(scope, SharingStarted.Lazily, 1)

  override val listDataFlow: Flow<List<GitLabMergeRequestDetails>> =
    loaderFlow.flatMapLatest { loader -> loader.stateFlow.mapNotNull { it.list?.map(GitLabMergeRequestDetails::fromRestDTO) } }
  override val loading: Flow<Boolean> = loaderFlow.flatMapLatest { it.isBusyFlow }
  override val error: Flow<Throwable?> = loaderFlow.flatMapLatest { loader -> loader.stateFlow.map { it.error } }

  init {
    scope.launchNow {
      tokenRefreshFlow.collect {
        loaderInitFlow.emit(Unit)
      }
    }
  }

  override fun requestMore() {
    scope.launch {
      loaderFlow.first().loadMore()
    }
  }

  override fun refresh() {
    scope.launch {
      loaderFlow.first().refresh()
    }
  }
}