// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil
import com.intellij.collaboration.ui.codereview.list.search.DropDownComponentFactory
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelFactory
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.PopupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.*
import org.jetbrains.plugins.gitlab.util.GitLabBundle
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
      if (author != null) append("""author:"${author}"""").append(" ")
      if (assignee != null) append("""assignee:"${assignee}"""").append(" ")
      if (reviewer != null) append("""reviewer:"${reviewer}"""").append(" ")
    }.toString()
  }

  override fun createFilters(viewScope: CoroutineScope): List<JComponent> = listOf(
    createStateFilter(viewScope),
    createAuthorFilter(viewScope),
    createAssigneeFilter(viewScope),
    createReviewerFilter(viewScope),
    createLabelFilter(viewScope)
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

  private fun createAuthorFilter(viewScope: CoroutineScope): JComponent = createParticipantFilter(
    viewScope,
    participantFilterState = vm.authorFilterState,
    filterName = GitLabBundle.message("merge.request.list.filter.category.author"),
    participantCreator = { user -> MergeRequestsAuthorFilterValue(user.username, user.name) }
  )

  private fun createAssigneeFilter(viewScope: CoroutineScope): JComponent = createParticipantFilter(
    viewScope,
    participantFilterState = vm.assigneeFilterState,
    filterName = GitLabBundle.message("merge.request.list.filter.category.assignee"),
    participantCreator = { user -> MergeRequestsAssigneeFilterValue(user.username, user.name) }
  )

  private fun createReviewerFilter(viewScope: CoroutineScope): JComponent = createParticipantFilter(
    viewScope,
    participantFilterState = vm.reviewerFilterState,
    filterName = GitLabBundle.message("merge.request.list.filter.category.reviewer"),
    participantCreator = { user -> MergeRequestsReviewerFilterValue(user.username, user.name) }
  )

  private fun createLabelFilter(viewScope: CoroutineScope): JComponent = DropDownComponentFactory(vm.labelFilterState).create(
    viewScope,
    filterName = GitLabBundle.message("merge.request.list.filter.category.label"),
    valuePresenter = { labelFilterValue -> labelFilterValue.title },
    chooseValue = { point, popupState ->
      ChooserPopupUtil.showAsyncChooserPopup(
        point, popupState,
        itemsLoader = { vm.getLabels().map { label -> LabelFilterValue(label.title) } },
        presenter = { labelFilterValue -> ChooserPopupUtil.PopupItemPresentation.Simple(shortText = labelFilterValue.title) }
      )
    }
  )

  private fun <T : MergeRequestsMemberFilterValue> createParticipantFilter(
    viewScope: CoroutineScope,
    participantFilterState: MutableStateFlow<T?>,
    filterName: @Nls String,
    participantCreator: (GitLabUserDTO) -> T
  ): JComponent = DropDownComponentFactory(participantFilterState).create(
    viewScope,
    filterName,
    valuePresenter = { participant -> participant.fullname },
    chooseValue = { point, popupState ->
      val selectedAuthor = showParticipantChooser(point, popupState, participantsLoader = {
        vm.getMergeRequestMembers().map { member -> member.user }
      })
      selectedAuthor?.let { user -> participantCreator(user) }
    })

  private suspend fun showParticipantChooser(
    point: RelativePoint,
    popupState: PopupState<JBPopup>,
    participantsLoader: suspend () -> List<GitLabUserDTO>
  ): GitLabUserDTO? {
    return ChooserPopupUtil.showAsyncChooserPopup(point, popupState, itemsLoader = { participantsLoader() }) { user ->
      ChooserPopupUtil.PopupItemPresentation.Simple(shortText = user.name, icon = vm.avatarIconsProvider.getIcon(user, AVATAR_SIZE))
    }
  }

  companion object {
    private const val AVATAR_SIZE = 20

    private fun getShortText(stateFilterValue: MergeRequestStateFilterValue): @Nls String = when (stateFilterValue) {
      MergeRequestStateFilterValue.OPENED -> GitLabBundle.message("merge.request.list.filter.state.open")
      MergeRequestStateFilterValue.MERGED -> GitLabBundle.message("merge.request.list.filter.state.merged")
      MergeRequestStateFilterValue.CLOSED -> GitLabBundle.message("merge.request.list.filter.state.closed")
    }
  }
}