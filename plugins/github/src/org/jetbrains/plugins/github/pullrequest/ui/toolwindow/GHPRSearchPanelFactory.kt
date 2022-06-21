// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.PopupItemPresentation
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.showAsyncChooserPopup
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.showChooserPopup
import com.intellij.collaboration.ui.codereview.list.search.DropDownComponentFactory
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelFactory
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchTextFieldFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent

internal class GHPRSearchPanelFactory(private val repositoryDataService: GHPRRepositoryDataService,
                                      private val searchState: MutableStateFlow<GHPRListSearchValue>) {

  fun create(vmScope: CoroutineScope, avatarIconsProvider: GHAvatarIconsProvider): JComponent {
    val vm = GHPRSearchPanelViewModel(vmScope, searchState)

    val searchField = ReviewListSearchTextFieldFactory(vm.queryState).create(vmScope)

    val filters = listOf(
      DropDownComponentFactory(vm.stateFilterState)
        .create(vmScope, GithubBundle.message("pull.request.list.filter.state"),
                GHPRListSearchValue.State.values().asList(),
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
                GHPRListSearchValue.ReviewState.values().asList(),
                ::getShortText) {
          PopupItemPresentation.Simple(getFullText(it))
        }
    )
    return ReviewListSearchPanelFactory().create(searchField, filters)
  }

  companion object {
    fun getShortText(stateFilterValue: GHPRListSearchValue.State): @Nls String = when (stateFilterValue) {
      GHPRListSearchValue.State.OPEN -> GithubBundle.message("pull.request.list.filter.state.open")
      GHPRListSearchValue.State.CLOSED -> GithubBundle.message("pull.request.list.filter.state.closed")
      GHPRListSearchValue.State.MERGED -> GithubBundle.message("pull.request.list.filter.state.merged")
    }

    fun getShortText(reviewStateFilterValue: GHPRListSearchValue.ReviewState): @Nls String = when (reviewStateFilterValue) {
      GHPRListSearchValue.ReviewState.NO_REVIEW -> GithubBundle.message("pull.request.list.filter.review.no.short")
      GHPRListSearchValue.ReviewState.REQUIRED -> GithubBundle.message("pull.request.list.filter.review.required.short")
      GHPRListSearchValue.ReviewState.APPROVED -> GithubBundle.message("pull.request.list.filter.review.approved.short")
      GHPRListSearchValue.ReviewState.CHANGES_REQUESTED -> GithubBundle.message("pull.request.list.filter.review.change.requested.short")
      GHPRListSearchValue.ReviewState.REVIEWED_BY_ME -> GithubBundle.message("pull.request.list.filter.review.reviewed.short")
      GHPRListSearchValue.ReviewState.NOT_REVIEWED_BY_ME -> GithubBundle.message("pull.request.list.filter.review.not.short")
      GHPRListSearchValue.ReviewState.AWAITING_REVIEW -> GithubBundle.message("pull.request.list.filter.review.awaiting.short")
    }

    fun getFullText(reviewStateFilterValue: GHPRListSearchValue.ReviewState): @Nls String = when (reviewStateFilterValue) {
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