// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.data.GHPRListLoader

internal class GHPRSearchPanelViewModel(
  private val scope: CoroutineScope,
  private val persistentHistory: GHPRListPersistentSearchHistory
) {

  val searchState = MutableStateFlow(searchHistory.lastOrNull() ?: GHPRListSearchValue.DEFAULT)

  val queryState = searchState.partialState(GHPRListSearchValue::searchQuery) {
    copy(searchQuery = it)
  }

  val stateFilterState = searchState.partialState(GHPRListSearchValue::state) {
    copy(state = it)
  }

  val authorFilterState = searchState.partialState(GHPRListSearchValue::author) {
    copy(author = it)
  }

  val labelFilterState = searchState.partialState(GHPRListSearchValue::label) {
    copy(label = it)
  }

  val assigneeFilterState = searchState.partialState(GHPRListSearchValue::assignee) {
    copy(assignee = it)
  }

  val reviewFilterState = searchState.partialState(GHPRListSearchValue::reviewState) {
    copy(reviewState = it)
  }

  private fun <T> MutableStateFlow<GHPRListSearchValue>.partialState(getter: (GHPRListSearchValue) -> T,
                                                                     updater: GHPRListSearchValue.(T?) -> GHPRListSearchValue)
    : MutableStateFlow<T?> {

    val filterState = MutableStateFlow<T?>(null)
    scope.launch {
      collectLatest { value ->
        filterState.update { getter(value) }
      }
    }
    scope.launch {
      filterState.collectLatest { value ->
        update { updater(it, value) }
      }
    }
    return filterState
  }

  val searchHistory: List<GHPRListSearchValue>
    get() = persistentHistory.history

  private var delayedHistoryAdditionJob: Job? = null

  init {
    scope.launch {
      searchState.distinctUntilChangedBy { it.searchQuery }.drop(1).collectLatest { addToHistory(it) }
    }
    scope.launch {
      searchState.drop(1).distinctUntilChanged { old, new ->
        old.copy(searchQuery = null) == new.copy(searchQuery = null)
      }.collectLatest { addToHistory(it, 10000) }
    }
  }

  private fun addToHistory(search: GHPRListSearchValue, delayMs: Long? = null) {
    delayedHistoryAdditionJob?.cancel()

    if (search.isEmpty || search == GHPRListSearchValue.DEFAULT) return

    if (persistentHistory.history.contains(search)) {
      doAdd(search)
      return
    }

    if (delayMs != null) {
      delayedHistoryAdditionJob = scope.launch {
        delay(delayMs)
        doAdd(search)
      }
    }
    else {
      doAdd(search)
    }
  }

  private fun doAdd(search: GHPRListSearchValue) {
    persistentHistory.history = persistentHistory.history.toMutableList().apply {
      remove(search)
      add(search)
    }
  }
}
