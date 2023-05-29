// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.api.page.SequentialListLoader
import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.ui.codereview.list.ReviewListViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.util.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModel.ListDataUpdate

internal interface GitLabMergeRequestsListViewModel : ReviewListViewModel {
  val filterVm: GitLabMergeRequestsFiltersViewModel
  val avatarIconsProvider: IconsProvider<GitLabUserDTO>
  val accountManager: GitLabAccountManager

  val repository: String
  val account: GitLabAccount

  val listDataFlow: Flow<ListDataUpdate>
  val canLoadMoreState: StateFlow<Boolean>

  val loadingState: StateFlow<Boolean>
  val errorState: StateFlow<Throwable?>

  fun requestMore()

  override fun refresh()

  sealed interface ListDataUpdate {
    class NewBatch(val newList: List<GitLabMergeRequestDetails>, val batch: List<GitLabMergeRequestDetails>) : ListDataUpdate
    object Clear : ListDataUpdate
  }
}

internal class GitLabMergeRequestsListViewModelImpl(
  parentCs: CoroutineScope,
  override val filterVm: GitLabMergeRequestsFiltersViewModel,
  override val repository: String,
  override val account: GitLabAccount,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  override val accountManager: GitLabAccountManager,
  private val tokenRefreshFlow: Flow<Unit>,
  private val loaderSupplier: (GitLabMergeRequestsFiltersValue) -> SequentialListLoader<GitLabMergeRequestDetails>)
  : GitLabMergeRequestsListViewModel {

  private val scope = parentCs.childScope(Dispatchers.Main)

  private val listState = mutableListOf<GitLabMergeRequestDetails>()
  private val _listDataFlow: MutableSharedFlow<ListDataUpdate> = MutableSharedFlow()
  override val listDataFlow: SharedFlow<ListDataUpdate> = _listDataFlow.asSharedFlow()

  private val loaderHasMoreState: MutableStateFlow<Boolean> = MutableStateFlow(true)

  private val _loadingState: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
  private val _errorState: MutableStateFlow<Throwable?> = MutableStateFlow(null)
  override val errorState: StateFlow<Throwable?> = _errorState.asStateFlow()

  private val loaderState = MutableStateFlow(loaderSupplier(filterVm.searchState.value))

  override val canLoadMoreState: StateFlow<Boolean> =
    combineState(scope, loaderHasMoreState, errorState) { loaderHasMore, error ->
      loaderHasMore && error == null
    }

  private val loadingRequestFlow = Channel<Unit>(1, BufferOverflow.DROP_LATEST)

  init {
    scope.launch {
      loaderState.collectLatest { loader ->
        loadingRequestFlow.receiveAsFlow().collect {
          try {
            handleLoadingRequest(loader)
          }
          catch (e: Exception) {
            // we do not want to cancel collect when there's a loading error
          }
        }
      }
    }

    scope.launch {
      filterVm.searchState
        .drop(1) // Skip initial emit
        .collect {
          doReset()
        }
    }

    scope.launch {
      tokenRefreshFlow.collect {
        doReset()
      }
    }
  }

  private suspend fun handleLoadingRequest(loader: SequentialListLoader<GitLabMergeRequestDetails>) {
    _loadingState.value = true
    try {
      val (data, hasMore) = loader.loadNext()
      listState.addAll(data)
      loaderHasMoreState.value = hasMore
      _listDataFlow.emit(ListDataUpdate.NewBatch(listState, data))
    }
    catch (e: Throwable) {
      if (e !is CancellationException) {
        _errorState.value = e
      }
      throw e
    }
    finally {
      _loadingState.value = false
    }
  }

  override fun requestMore() {
    scope.launch {
      loadingRequestFlow.send(Unit)
    }
  }

  override fun refresh() {
    scope.launch {
      doReset()
    }
  }

  private suspend fun doReset() {
    loaderState.value = loaderSupplier(filterVm.searchState.value)
    listState.clear()
    _errorState.value = null
    _listDataFlow.emit(ListDataUpdate.Clear)

    requestMore()
  }
}