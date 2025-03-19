// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil
import com.intellij.collaboration.ui.codereview.list.search.DropDownComponentFactory
import com.intellij.collaboration.ui.codereview.list.search.PopupConfig
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelFactory
import com.intellij.collaboration.ui.util.popup.PopupItemPresentation
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestsMemberFilterValue.*
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
      if (author != null) append("""author:"${author.username}"""").append(" ")
      if (assignee != null) append("""assignee:"${assignee.username}"""").append(" ")
      if (reviewer != null) append("""reviewer:"${reviewer.username}"""").append(" ")
      if (label != null) append("""label:"${label.title}"""").append(" ")
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
    is GitLabMergeRequestsQuickFilter.IncludeMyChanges -> GitLabBundle.message("merge.request.list.filter.quick.me.author")
    is GitLabMergeRequestsQuickFilter.NeedMyReview -> GitLabBundle.message("merge.request.list.filter.quick.me.reviewer")
    is GitLabMergeRequestsQuickFilter.AssignedToMe -> GitLabBundle.message("merge.request.list.filter.quick.me.assignee")
    is GitLabMergeRequestsQuickFilter.Closed -> GitLabBundle.message("merge.request.list.filter.quick.closed")
  }

  private fun createStateFilter(viewScope: CoroutineScope): JComponent {
    return DropDownComponentFactory(vm.stateFilterState).create(
      viewScope,
      filterName = GitLabBundle.message("merge.request.list.filter.category.state"),
      items = listOf(MergeRequestStateFilterValue.OPENED, MergeRequestStateFilterValue.MERGED, MergeRequestStateFilterValue.CLOSED),
      onSelect = {},
      valuePresenter = ::getShortText
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
    chooseValue = { point ->
      ChooserPopupUtil.showAsyncChooserPopup<LabelFilterValue>(
        point,
        itemsLoader = vm.labels.mapResultList { label -> LabelFilterValue(label.title) },
        presenter = { labelFilterValue -> PopupItemPresentation.Simple(shortText = labelFilterValue.title) }
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
    chooseValue = { point ->
      val selectedAuthor = showParticipantChooser(point, participantsLoader = vm.mergeRequestMembers)
      selectedAuthor?.let { user -> participantCreator(user) }
    })

  private suspend fun showParticipantChooser(
    point: RelativePoint,
    participantsLoader: Flow<Result<List<GitLabUserDTO>>>
  ): GitLabUserDTO? = ChooserPopupUtil.showAsyncChooserPopup(
    point, itemsLoader = participantsLoader,
    presenter = { user ->
      PopupItemPresentation.Simple(shortText = user.name, icon = vm.avatarIconsProvider.getIcon(user, Avatar.Sizes.BASE))
    },
    popupConfig = PopupConfig(errorPresenter = ErrorStatusPresenter.simple(
      GitLabBundle.message("merge.request.list.filter.error"),
      descriptionProvider = { null },
      actionProvider = {
        swingAction(GitLabBundle.message("merge.request.list.filter.error.action")) {
          vm.reloadData()
        }
      }
    ))
  )

  companion object {
    private fun getShortText(stateFilterValue: MergeRequestStateFilterValue): @Nls String = when (stateFilterValue) {
      MergeRequestStateFilterValue.OPENED -> GitLabBundle.message("merge.request.list.filter.state.open")
      MergeRequestStateFilterValue.MERGED -> GitLabBundle.message("merge.request.list.filter.state.merged")
      MergeRequestStateFilterValue.CLOSED -> GitLabBundle.message("merge.request.list.filter.state.closed")
    }
  }
}

private fun <T, R> Flow<Result<List<T>>>.mapResultList(mapper: (T) -> R): Flow<Result<List<R>>> {
  val flow = this
  return flow.map { result: Result<List<T>> ->
    result.map { list: List<T> ->
      list.map { item: T ->
        mapper(item)
      }
    }
  }
}