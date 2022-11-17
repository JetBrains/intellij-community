// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.Disposable
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader

internal class GHPRListPanelController(
  scope: CoroutineScope,
  private val listLoader: GHListLoader<*>,
  private val searchVm: GHPRSearchPanelViewModel,
  private val emptyText: StatusText,
  private val repository: String,
  listenersDisposable: Disposable
) {
  init {
    listLoader.addLoadingStateChangeListener(listenersDisposable, ::update)
    scope.launch {
      searchVm.searchState.collect {
        update()
      }
    }
  }

  private fun update() {
    emptyText.clear()
    if (listLoader.error != null) return

    if (listLoader.loading) {
      emptyText.appendText(CollaborationToolsBundle.message("review.list.empty.state.loading"))
      return
    }

    val search = searchVm.searchState.value
    if (search.filterCount == 0) {
      emptyText.appendText(GithubBundle.message("pull.request.list.nothing.loaded", repository))
    }
    else {
      emptyText
        .appendText(GithubBundle.message("pull.request.list.no.matches"))
        .appendSecondaryText(GithubBundle.message("pull.request.list.filters.clear"), SimpleTextAttributes.LINK_ATTRIBUTES) {
          searchVm.searchState.value = GHPRListSearchValue.EMPTY
        }
    }
  }
}