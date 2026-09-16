// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.list.ReviewListViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.AsyncIncrementalListComputer
import com.intellij.collaboration.util.ComputableSequence
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.collaboration.util.ListPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModel

@ApiStatus.Internal
interface GitLabMergeRequestsListViewModel : ReviewListViewModel {
  val filterVm: GitLabMergeRequestsFiltersViewModel
  val avatarIconsProvider: IconsProvider<GitLabUserDTO>

  val repository: String

  val listDataFlow: StateFlow<List<GitLabMergeRequestDetails>>

  val loading: StateFlow<Boolean>
  val error: StateFlow<Throwable?>

  /**
   * Emits whenever the list is reloaded or refreshed. Consumers can use this to re-run branch-scoped lookups
   * (e.g. "which MR corresponds to the current branch") when the user refreshes the merge request list.
   */
  val listUpdated: Flow<Unit>

  fun requestMore()
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestsListViewModelImpl(
  parentCs: CoroutineScope,
  override val filterVm: GitLabMergeRequestsFiltersViewModel,
  override val repository: String,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  private val loaderSupplier: (GitLabMergeRequestsFiltersValue) -> ComputableSequence<ListPart<GitLabMergeRequestDetails>>,
) : GitLabMergeRequestsListViewModel {
  private val cs = parentCs.childScope(this::class)
  private val reloadSignal = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val loaderState =
    filterVm.searchState.map { search -> loaderSupplier(search) }
      .flatMapLatest { sequence ->
        reloadSignal.withInitial(Unit).mapScoped(true) {
          AsyncIncrementalListComputer.createIn(this, sequence).apply {
            requestMore()
          }
        }
      }
      .stateIn(cs, SharingStarted.Eagerly, null)

  private val listUpdatedSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  override val listUpdated: Flow<Unit> = listUpdatedSignal.asSharedFlow()

  private val loaderStateFlow = loaderState.flatMapLatest { it?.state ?: flowOf(IncrementallyComputedValue.initial()) }
  override val listDataFlow: StateFlow<List<GitLabMergeRequestDetails>> =
    loaderStateFlow.map { it.valueOrNull ?: emptyList() }
      .stateIn(cs, SharingStarted.Eagerly, emptyList())
  override val loading: StateFlow<Boolean> =
    loaderStateFlow.map { it.isLoading }
      .stateIn(cs, SharingStarted.Eagerly, false)
  override val error: StateFlow<Throwable?> =
    loaderStateFlow.map { it.exceptionOrNull }
      .stateIn(cs, SharingStarted.Eagerly, null)

  override fun requestMore() {
    loaderState.value?.requestMore()
  }

  override fun refresh() {
    reloadSignal.tryEmit(Unit)
    listUpdatedSignal.tryEmit(Unit)
  }
}