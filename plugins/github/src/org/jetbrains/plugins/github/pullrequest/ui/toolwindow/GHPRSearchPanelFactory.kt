// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.PopupItemPresentation
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.showAsyncChooserPopup
import com.intellij.collaboration.ui.codereview.list.search.DropDownComponentFactory
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelFactory
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchTextFieldFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent

internal class GHPRSearchPanelFactory(private val searchState: MutableStateFlow<GHPRListSearchState>,
                                      private val repositoryDataService: GHPRRepositoryDataService) {

  fun create(vmScope: CoroutineScope, avatarIconsProvider: GHAvatarIconsProvider): JComponent {
    val vm = GHPRSearchPanelViewModel(vmScope, searchState)

    val searchField = ReviewListSearchTextFieldFactory(vm.queryState).create(vmScope)

    val filters = listOf(
      DropDownComponentFactory(vm.stateFilterState)
        .create(vmScope, GithubBundle.message("pull.request.list.filter.state"),
                GHPRListSearchState.State.values().asList(),
                ::getShortText),
      DropDownComponentFactory(vm.authorFilterState)
        .create(vmScope, GithubBundle.message("pull.request.list.filter.author")) { point ->
          showAsyncChooserPopup(point, { repositoryDataService.collaborators.await() }) {
            PopupItemPresentation.Simple(it.shortName, avatarIconsProvider.getIcon(it.avatarUrl), it.name)
          }?.login
        },
      DropDownComponentFactory(vm.labelFilterState)
        .create(vmScope, GithubBundle.message("pull.request.list.filter.label")) { point ->
          showAsyncChooserPopup(point, { repositoryDataService.labels.await() }) {
            PopupItemPresentation.Simple(it.name)
          }?.name
        },
      DropDownComponentFactory(vm.assigneeFilterState)
        .create(vmScope, GithubBundle.message("pull.request.list.filter.assignee")) { point ->
          showAsyncChooserPopup(point, { repositoryDataService.issuesAssignees.await() }) {
            PopupItemPresentation.Simple(it.shortName, avatarIconsProvider.getIcon(it.avatarUrl), it.name)
          }?.login
        },
      DropDownComponentFactory(vm.reviewFilterState)
        .create(vmScope, GithubBundle.message("pull.request.list.filter.review"),
                GHPRListSearchState.ReviewState.values().asList(),
                ::getShortText) {
          PopupItemPresentation.Simple(getFullText(it))
        }
    )
    return ReviewListSearchPanelFactory().create(searchField, filters)
  }

  companion object {
    fun getShortText(stateFilterValue: GHPRListSearchState.State): @Nls String = when (stateFilterValue) {
      GHPRListSearchState.State.OPEN -> GithubBundle.message("pull.request.list.filter.state.open")
      GHPRListSearchState.State.CLOSED -> GithubBundle.message("pull.request.list.filter.state.closed")
      GHPRListSearchState.State.MERGED -> GithubBundle.message("pull.request.list.filter.state.merged")
    }

    fun getShortText(reviewStateFilterValue: GHPRListSearchState.ReviewState): @Nls String = when (reviewStateFilterValue) {
      GHPRListSearchState.ReviewState.NO_REVIEW -> GithubBundle.message("pull.request.list.filter.review.no.short")
      GHPRListSearchState.ReviewState.REQUIRED -> GithubBundle.message("pull.request.list.filter.review.required.short")
      GHPRListSearchState.ReviewState.APPROVED -> GithubBundle.message("pull.request.list.filter.review.approved.short")
      GHPRListSearchState.ReviewState.CHANGES_REQUESTED -> GithubBundle.message("pull.request.list.filter.review.change.requested.short")
      GHPRListSearchState.ReviewState.REVIEWED_BY_ME -> GithubBundle.message("pull.request.list.filter.review.reviewed.short")
      GHPRListSearchState.ReviewState.NOT_REVIEWED_BY_ME -> GithubBundle.message("pull.request.list.filter.review.not.short")
      GHPRListSearchState.ReviewState.AWAITING_REVIEW -> GithubBundle.message("pull.request.list.filter.review.awaiting.short")
    }

    fun getFullText(reviewStateFilterValue: GHPRListSearchState.ReviewState): @Nls String = when (reviewStateFilterValue) {
      GHPRListSearchState.ReviewState.NO_REVIEW -> GithubBundle.message("pull.request.list.filter.review.no.full")
      GHPRListSearchState.ReviewState.REQUIRED -> GithubBundle.message("pull.request.list.filter.review.required.full")
      GHPRListSearchState.ReviewState.APPROVED -> GithubBundle.message("pull.request.list.filter.review.approved.full")
      GHPRListSearchState.ReviewState.CHANGES_REQUESTED -> GithubBundle.message("pull.request.list.filter.review.change.requested.full")
      GHPRListSearchState.ReviewState.REVIEWED_BY_ME -> GithubBundle.message("pull.request.list.filter.review.reviewed.full")
      GHPRListSearchState.ReviewState.NOT_REVIEWED_BY_ME -> GithubBundle.message("pull.request.list.filter.review.not.full")
      GHPRListSearchState.ReviewState.AWAITING_REVIEW -> GithubBundle.message("pull.request.list.filter.review.awaiting.full")
    }
  }
}