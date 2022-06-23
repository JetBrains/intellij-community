// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.PopupItemPresentation
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.showAsyncChooserPopup
import com.intellij.collaboration.ui.codereview.list.search.DropDownComponentFactory
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.i18n.GithubBundle
import javax.swing.JComponent

internal class GHPRSearchPanelFactory(vm: GHPRSearchPanelViewModel) :
  ReviewListSearchPanelFactory<GHPRListSearchValue, GHPRSearchPanelViewModel>(vm) {

  override fun getShortText(searchValue: GHPRListSearchValue): @Nls String = with(searchValue) {
    @Suppress("HardCodedStringLiteral")
    StringBuilder().apply {
      if (searchQuery != null) append(""""$searchQuery"""").append(" ")
      if (state != null) append("""state:"${getShortText(state)}"""").append(" ")
      if (label != null) append("label:$label").append(" ")
      if (assignee != null) append("assignee:$assignee").append(" ")
      if (reviewState != null) append("""reviewState:"${getShortText(reviewState)}"""").append(" ")
      if (author != null) append("author:$author").append(" ")
    }.toString()
  }

  override fun createFilters(viewScope: CoroutineScope): List<JComponent> = listOf(
    DropDownComponentFactory(vm.stateFilterState)
      .create(viewScope, GithubBundle.message("pull.request.list.filter.state"),
              GHPRListSearchValue.State.values().asList(),
              ::getShortText),
    DropDownComponentFactory(vm.authorFilterState)
      .create(viewScope, GithubBundle.message("pull.request.list.filter.author")) { point ->
        showAsyncChooserPopup(point, { vm.getAuthors() }) {
          PopupItemPresentation.Simple(it.shortName, vm.avatarIconsProvider.getIcon(it.avatarUrl), it.name)
        }?.login
      },
    DropDownComponentFactory(vm.labelFilterState)
      .create(viewScope, GithubBundle.message("pull.request.list.filter.label")) { point ->
        showAsyncChooserPopup(point, { vm.getLabels() }) {
          PopupItemPresentation.Simple(it.name)
        }?.name
      },
    DropDownComponentFactory(vm.assigneeFilterState)
      .create(viewScope, GithubBundle.message("pull.request.list.filter.assignee")) { point ->
        showAsyncChooserPopup(point, { vm.getAssignees() }) {
          PopupItemPresentation.Simple(it.shortName, vm.avatarIconsProvider.getIcon(it.avatarUrl), it.name)
        }?.login
      },
    DropDownComponentFactory(vm.reviewFilterState)
      .create(viewScope, GithubBundle.message("pull.request.list.filter.review"),
              GHPRListSearchValue.ReviewState.values().asList(),
              ::getShortText) {
        PopupItemPresentation.Simple(getFullText(it))
      }
  )

  companion object {
    private fun getShortText(stateFilterValue: GHPRListSearchValue.State): @Nls String = when (stateFilterValue) {
      GHPRListSearchValue.State.OPEN -> GithubBundle.message("pull.request.list.filter.state.open")
      GHPRListSearchValue.State.CLOSED -> GithubBundle.message("pull.request.list.filter.state.closed")
      GHPRListSearchValue.State.MERGED -> GithubBundle.message("pull.request.list.filter.state.merged")
    }

    private fun getShortText(reviewStateFilterValue: GHPRListSearchValue.ReviewState): @Nls String = when (reviewStateFilterValue) {
      GHPRListSearchValue.ReviewState.NO_REVIEW -> GithubBundle.message("pull.request.list.filter.review.no.short")
      GHPRListSearchValue.ReviewState.REQUIRED -> GithubBundle.message("pull.request.list.filter.review.required.short")
      GHPRListSearchValue.ReviewState.APPROVED -> GithubBundle.message("pull.request.list.filter.review.approved.short")
      GHPRListSearchValue.ReviewState.CHANGES_REQUESTED -> GithubBundle.message("pull.request.list.filter.review.change.requested.short")
      GHPRListSearchValue.ReviewState.REVIEWED_BY_ME -> GithubBundle.message("pull.request.list.filter.review.reviewed.short")
      GHPRListSearchValue.ReviewState.NOT_REVIEWED_BY_ME -> GithubBundle.message("pull.request.list.filter.review.not.short")
      GHPRListSearchValue.ReviewState.AWAITING_REVIEW -> GithubBundle.message("pull.request.list.filter.review.awaiting.short")
    }

    private fun getFullText(reviewStateFilterValue: GHPRListSearchValue.ReviewState): @Nls String = when (reviewStateFilterValue) {
      GHPRListSearchValue.ReviewState.NO_REVIEW -> GithubBundle.message("pull.request.list.filter.review.no.full")
      GHPRListSearchValue.ReviewState.REQUIRED -> GithubBundle.message("pull.request.list.filter.review.required.full")
      GHPRListSearchValue.ReviewState.APPROVED -> GithubBundle.message("pull.request.list.filter.review.approved.full")
      GHPRListSearchValue.ReviewState.CHANGES_REQUESTED -> GithubBundle.message("pull.request.list.filter.review.change.requested.full")
      GHPRListSearchValue.ReviewState.REVIEWED_BY_ME -> GithubBundle.message("pull.request.list.filter.review.reviewed.full")
      GHPRListSearchValue.ReviewState.NOT_REVIEWED_BY_ME -> GithubBundle.message("pull.request.list.filter.review.not.full")
      GHPRListSearchValue.ReviewState.AWAITING_REVIEW -> GithubBundle.message("pull.request.list.filter.review.awaiting.full")
    }
  }
}