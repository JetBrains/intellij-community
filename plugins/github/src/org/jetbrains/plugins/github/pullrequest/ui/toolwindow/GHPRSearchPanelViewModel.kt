// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.codereview.list.search.*
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.PopupItemPresentation
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.showAsyncChooserPopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent

internal class GHPRSearchPanelViewModel(private val scope: CoroutineScope,
                                        searchState: MutableStateFlow<GHPRListSearchState>) {

  val queryState = searchState.partialState(500L, GHPRListSearchState::searchQuery) {
    copy(searchQuery = it)
  }

  val stateFilterState = searchState.partialState(GHPRListSearchState::state) {
    copy(state = it)
  }

  val authorFilterState = searchState.partialState(GHPRListSearchState::author) {
    copy(author = it)
  }

  val labelFilterState = searchState.partialState(GHPRListSearchState::label) {
    copy(label = it)
  }

  val assigneeFilterState = searchState.partialState(GHPRListSearchState::assignee) {
    copy(assignee = it)
  }

  val reviewFilterState = searchState.partialState(GHPRListSearchState::reviewState) {
    copy(reviewState = it)
  }

  private fun <T> MutableStateFlow<GHPRListSearchState>.partialState(getter: (GHPRListSearchState) -> T,
                                                                     updater: GHPRListSearchState.(T?) -> GHPRListSearchState)
    : MutableStateFlow<T?> = partialState(null, getter, updater)

  @OptIn(FlowPreview::class)
  private fun <T> MutableStateFlow<GHPRListSearchState>.partialState(debounce: Long? = null,
                                                                     getter: (GHPRListSearchState) -> T,
                                                                     updater: GHPRListSearchState.(T?) -> GHPRListSearchState)
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