// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.api.page.SequentialListLoader
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.ui.codereview.list.ReviewListViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.util.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModel.ListDataUpdate
import java.util.concurrent.CopyOnWriteArrayList

internal interface GitLabMergeRequestsListViewModel : ReviewListViewModel {
  val filterVm: GitLabMergeRequestsFiltersViewModel
  val avatarIconsProvider: IconsProvider<GitLabUserDTO>

  val repository: String

  val listDataFlow: Flow<ListDataUpdate>

  val loading: Flow<Boolean>
  val error: Flow<Throwable?>

  fun requestMore()

  override fun refresh()

  sealed interface ListDataUpdate {
    class NewBatch(val newList: List<GitLabMergeRequestDetails>, val batch: List<GitLabMergeRequestDetails>) : ListDataUpdate
    object Clear : ListDataUpdate
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabMergeRequestsListViewModelImpl(
  parentCs: CoroutineScope,
  override val filterVm: GitLabMergeRequestsFiltersViewModel,
  override val repository: String,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  private val tokenRefreshFlow: Flow<Unit>,
  private val loaderSupplier: (GitLabMergeRequestsFiltersValue) -> SequentialListLoader<GitLabMergeRequestDetails>)
  : GitLabMergeRequestsListViewModel {

  private val scope = parentCs.childScope()

  private val loaderInitFlow = MutableSharedFlow<Unit>()

  private var loaderFlow: Flow<Loader> = flow {
    emit(Unit)
    loaderInitFlow.collect {
      emit(Unit)
    }
  }.combine(filterVm.searchState) { startNow, search ->
    startNow to search
  }.mapScoped { (_, search) ->
    Loader(this, loaderSupplier(search)).apply {
      requestMore()
    }
  }.shareIn(scope, SharingStarted.Lazily, 1)

  override val listDataFlow: Flow<ListDataUpdate> = loaderFlow.transformLatest {
    try {
      emitAll(it.listDataFlow)
    }
    catch (e: Exception) {
      emit(ListDataUpdate.Clear)
    }
  }
  override val loading: Flow<Boolean> = loaderFlow.flatMapLatest { it.loadingState }
  override val error: Flow<Throwable?> = loaderFlow.flatMapLatest { it.errorState }

  init {
    scope.launchNow {
      tokenRefreshFlow.collect {
        loaderInitFlow.emit(Unit)
      }
    }
  }

  override fun requestMore() {
    scope.launch {
      loaderFlow.first().requestMore()
    }
  }

  override fun refresh() {
    scope.launch {
      loaderInitFlow.emit(Unit)
    }
  }

  private class Loader(private val cs: CoroutineScope, private val loader: SequentialListLoader<GitLabMergeRequestDetails>) {
    private val listState = CopyOnWriteArrayList<GitLabMergeRequestDetails>()

    val listDataFlow: MutableSharedFlow<ListDataUpdate> = MutableSharedFlow(1)
    val loadingState: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val errorState: MutableStateFlow<Throwable?> = MutableStateFlow(null)

    @Volatile
    private var hasMoreBatches = true

    fun requestMore() {
      if (loadingState.value || errorState.value != null || !hasMoreBatches) return
      loadingState.value = true
      cs.launch {
        try {
          val (data, hasMore) = loader.loadNext()
          listState.addAll(data)
          hasMoreBatches = hasMore

          listDataFlow.emit(ListDataUpdate.NewBatch(listState.toList(), data))
        }
        catch (ce: CancellationException) {
          throw ce
        }
        catch (e: Throwable) {
          errorState.value = e
        }
        finally {
          loadingState.value = false
        }
      }
    }
  }
}