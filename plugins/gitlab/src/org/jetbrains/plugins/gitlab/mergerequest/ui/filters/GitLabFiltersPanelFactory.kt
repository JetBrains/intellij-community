// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.DropDownComponentFactory
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelFactory
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestStateFilterValue
import org.jetbrains.plugins.gitlab.ui.GitLabBundle
import javax.swing.JComponent

internal class GitLabFiltersPanelFactory(
  viewModel: GitLabMergeRequestsFiltersViewModel
) : ReviewListSearchPanelFactory<GitLabMergeRequestsFiltersValue, GitLabMergeRequestsQuickFilter, GitLabMergeRequestsFiltersViewModel>(
  viewModel
) {
  override fun getShortText(searchValue: GitLabMergeRequestsFiltersValue): @Nls String = with(searchValue) {
    @Suppress("HardCodedStringLiteral")
    StringBuilder().apply {
      if (searchQuery != null) append(""""$searchQuery"""").append(" ")
      if (state != null) append("""state:"${getShortText(state)}"""").append(" ")
    }.toString()
  }

  override fun createFilters(viewScope: CoroutineScope): List<JComponent> = listOf(
    createStateFilter(viewScope)
  )

  override fun GitLabMergeRequestsQuickFilter.getQuickFilterTitle(): String = when (this) {
    is GitLabMergeRequestsQuickFilter.Open -> GitLabBundle.message("merge.request.list.filter.quick.open")
  }

  private fun createStateFilter(viewScope: CoroutineScope): JComponent {
    return DropDownComponentFactory(vm.stateFilterState).create(
      viewScope,
      GitLabBundle.message("merge.request.list.filter.category.state"),
      listOf(MergeRequestStateFilterValue.OPENED, MergeRequestStateFilterValue.MERGED, MergeRequestStateFilterValue.CLOSED),
      ::getShortText
    )
  }

  companion object {
    private fun getShortText(stateFilterValue: MergeRequestStateFilterValue): @Nls String = when (stateFilterValue) {
      MergeRequestStateFilterValue.OPENED -> GitLabBundle.message("merge.request.list.filter.state.open")
      MergeRequestStateFilterValue.MERGED -> GitLabBundle.message("merge.request.list.filter.state.merged")
      MergeRequestStateFilterValue.CLOSED -> GitLabBundle.message("merge.request.list.filter.state.closed")
    }
  }
}