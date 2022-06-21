// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.codereview.list.search.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class GHPRSearchPanelViewModel(private val scope: CoroutineScope,
                                        searchState: MutableStateFlow<GHPRListSearchValue>) {

  val queryState = searchState.partialState(500L, GHPRListSearchValue::searchQuery) {
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
    : MutableStateFlow<T?> = partialState(null, getter, updater)

  @OptIn(FlowPreview::class)
  private fun <T> MutableStateFlow<GHPRListSearchValue>.partialState(debounce: Long? = null,
                                                                     getter: (GHPRListSearchValue) -> T,
                                                                     updater: GHPRListSearchValue.(T?) -> GHPRListSearchValue)
    : MutableStateFlow<T?> {

    val filterState = MutableStateFlow<T?>(null)
    scope.launch {
      collectLatest { value ->
        filterState.update { getter(value) }
      }
    }
    scope.launch {
      if (debounce != null) {
        filterState.debounce(debounce)
      }
      else {
        filterState
      }.collectLatest { value ->
        update { updater(it, value) }
      }
    }
    return filterState
  }
}