// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.async.ReloadablePotentiallyInfiniteListLoader
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.list.ReviewListViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.diagnostic.Logger
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
  tokenRefreshFlow: Flow<Unit>,
  private val loaderSupplier: (CoroutineScope, GitLabMergeRequestsFiltersValue) -> ReloadablePotentiallyInfiniteListLoader<GitLabMergeRequestShortRestDTO>,
) : GitLabMergeRequestsListViewModel {

  private val scope = parentCs.childScope("GL MR List VM")
  private val requestMoreLauncher = SingleCoroutineLauncher(scope.childScope("Request More"))

  private val loaderFlow: Flow<ReloadablePotentiallyInfiniteListLoader<GitLabMergeRequestShortRestDTO>> =
    filterVm.searchState
      .combine(tokenRefreshFlow.withInitial(Unit)) { search, _ -> search }
      .mapScoped { search -> loaderSupplier(this, search) }
      .shareIn(scope, SharingStarted.Lazily, 1)

  override val listDataFlow: Flow<List<GitLabMergeRequestDetails>> =
    loaderFlow.flatMapLatest { loader -> loader.stateFlow.mapNotNull { it.list?.map(GitLabMergeRequestDetails::fromRestDTO) } }
      .modelFlow(scope, LOG)
  override val loading: Flow<Boolean> = loaderFlow.flatMapLatest { it.isBusyFlow }.modelFlow(scope, LOG)
  override val error: Flow<Throwable?> = loaderFlow.flatMapLatest { loader -> loader.stateFlow.map { it.error } }.modelFlow(scope, LOG)

  override fun requestMore() {
    requestMoreLauncher.launch {
      loaderFlow.first().loadMore()
    }
  }

  override fun refresh() {
    scope.launch {
      loaderFlow.first().refresh()
    }
  }

  companion object {
    private val LOG = Logger.getInstance(GitLabMergeRequestsListViewModel::class.java)
  }
}