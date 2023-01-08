// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelViewModel
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelViewModelBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestStateFilterValue

internal interface GitLabMergeRequestsFiltersViewModel : ReviewListSearchPanelViewModel<GitLabMergeRequestsFiltersValue, GitLabMergeRequestsQuickFilter> {
  val stateFilterState: MutableStateFlow<MergeRequestStateFilterValue?>
}

internal class GitLabMergeRequestsFiltersViewModelImpl(
  scope: CoroutineScope,
  historyModel: GitLabMergeRequestsFiltersHistoryModel
) : GitLabMergeRequestsFiltersViewModel,
    ReviewListSearchPanelViewModelBase<GitLabMergeRequestsFiltersValue, GitLabMergeRequestsQuickFilter>(
      scope,
      historyModel,
      emptySearch = GitLabMergeRequestsFiltersValue.EMPTY,
      defaultQuickFilter = GitLabMergeRequestsQuickFilter.Open()
    ) {
  override fun GitLabMergeRequestsFiltersValue.withQuery(query: String?): GitLabMergeRequestsFiltersValue {
    return copy(searchQuery = query)
  }

  override val quickFilters: List<GitLabMergeRequestsQuickFilter> = listOf(
    GitLabMergeRequestsQuickFilter.Open()
  )

  override val stateFilterState = searchState.partialState(GitLabMergeRequestsFiltersValue::state) {
    copy(state = it)
  }
}