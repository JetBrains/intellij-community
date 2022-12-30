// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.api.page.SequentialListLoader
import com.intellij.collaboration.async.combineState
import com.intellij.util.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestsListViewModel.ListDataUpdate

internal interface GitLabMergeRequestsListViewModel {
  val listDataFlow: Flow<ListDataUpdate>
  val canLoadMoreState: StateFlow<Boolean>

  val loadingState: StateFlow<Boolean>
  val errorState: StateFlow<Throwable?>

  fun requestMore()

  fun reset()

  sealed interface ListDataUpdate {
    class NewBatch(val newList: List<GitLabMergeRequestShortDTO>, val batch: List<GitLabMergeRequestShortDTO>) : ListDataUpdate
    object Clear : ListDataUpdate
  }
}

internal class GitLabMergeRequestsListViewModelImpl(
  parentCs: CoroutineScope,
  private val loaderSupplier: () -> SequentialListLoader<GitLabMergeRequestShortDTO>)
  : GitLabMergeRequestsListViewModel {

  private val scope = parentCs.childScope(Dispatchers.Main)

  private val loaderState = MutableStateFlow(loaderSupplier())

  private val listState = mutableListOf<GitLabMergeRequestShortDTO>()
  private val _listDataFlow: MutableSharedFlow<ListDataUpdate> = MutableSharedFlow()
  override val listDataFlow: SharedFlow<ListDataUpdate> = _listDataFlow.asSharedFlow()

  private val loaderHasMoreState: MutableStateFlow<Boolean> = MutableStateFlow(true)

  private val _loadingState: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
  private val _errorState: MutableStateFlow<Throwable?> = MutableStateFlow(null)
  override val errorState: StateFlow<Throwable?> = _errorState.asStateFlow()

  override val canLoadMoreState: StateFlow<Boolean> =
    combineState(scope, loaderHasMoreState, loadingState, errorState) { loaderHasMore, loading, error ->
      loaderHasMore && !loading && error == null
    }

  private val loadingRequestFlow = MutableSharedFlow<Unit>()

  init {
    scope.launch {
      loaderState.collectLatest { loader ->
        loadingRequestFlow.collect {
          try {
            handleLoadingRequest(loader)
          }
          catch (e: Exception) {
            // we do not want to cancel collect when there's a loading error
          }
        }
      }
    }
  }

  private suspend fun handleLoadingRequest(loader: SequentialListLoader<GitLabMergeRequestShortDTO>) {
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
      loadingRequestFlow.emit(Unit)
    }
  }

  override fun reset() {
    scope.launch {
      loaderState.value = loaderSupplier()
      listState.clear()
      _errorState.value = null
      _listDataFlow.emit(ListDataUpdate.Clear)
    }
  }
}
