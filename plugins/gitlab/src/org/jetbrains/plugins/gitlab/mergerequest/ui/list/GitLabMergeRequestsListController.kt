// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestsListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal class GitLabMergeRequestsListController(
  scope: CoroutineScope,
  private val listVm: GitLabMergeRequestsListViewModel,
  private val emptyText: StatusText
) {
  init {
    scope.launch {
      combine(listVm.loadingState, listVm.filterVm.searchState) { isLoading, searchState ->
        isLoading to searchState
      }.collect { (isLoading, searchState) ->
        update(isLoading, searchState, listVm.repository)
      }
    }
  }

  private fun update(isLoading: Boolean, searchState: GitLabMergeRequestsFiltersValue, repository: String) {
    emptyText.clear()

    if (isLoading) {
      emptyText.appendText(GitLabBundle.message("merge.request.list.empty.state.loading"))
      return
    }

    if (searchState.filterCount == 0) {
      emptyText
        .appendText(GitLabBundle.message("merge.request.list.empty.state.matching.nothing", repository))
    }
    else {
      emptyText
        .appendText(GitLabBundle.message("merge.request.list.empty.state.matching.nothing.with.filters"))
        .appendSecondaryText(GitLabBundle.message("merge.request.list.empty.state.clear.filters"), SimpleTextAttributes.LINK_ATTRIBUTES) {
          listVm.filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
        }
    }
  }
}