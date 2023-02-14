// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelViewModel
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelViewModelBase
import com.intellij.collaboration.ui.icon.IconsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.gitlab.api.data.GitLabAccessLevel
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestStateFilterValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestsMemberFilterValue

internal interface GitLabMergeRequestsFiltersViewModel : ReviewListSearchPanelViewModel<GitLabMergeRequestsFiltersValue, GitLabMergeRequestsQuickFilter> {
  val currentUser: GitLabUserDTO
  val avatarIconsProvider: IconsProvider<GitLabUserDTO>

  val stateFilterState: MutableStateFlow<MergeRequestStateFilterValue?>
  val authorFilterState: MutableStateFlow<MergeRequestsMemberFilterValue?>
  val assigneeFilterState: MutableStateFlow<MergeRequestsMemberFilterValue?>
  val reviewerFilterState: MutableStateFlow<MergeRequestsMemberFilterValue?>
  val labelFilterState: MutableStateFlow<GitLabMergeRequestsFiltersValue.LabelFilterValue?>

  suspend fun getMergeRequestMembers(): List<GitLabMemberDTO>

  suspend fun getLabels(): List<GitLabLabelDTO>
}

internal class GitLabMergeRequestsFiltersViewModelImpl(
  scope: CoroutineScope,
  historyModel: GitLabMergeRequestsFiltersHistoryModel,
  override val currentUser: GitLabUserDTO,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  private val projectData: GitLabProject
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
    GitLabMergeRequestsQuickFilter.Open(),
    GitLabMergeRequestsQuickFilter.IncludeMyChanges(currentUser),
    GitLabMergeRequestsQuickFilter.NeedMyReview(currentUser),
    GitLabMergeRequestsQuickFilter.AssignedToMe(currentUser),
    GitLabMergeRequestsQuickFilter.Closed(),
  )

  override val stateFilterState = searchState.partialState(GitLabMergeRequestsFiltersValue::state) {
    copy(state = it)
  }

  override val authorFilterState = searchState.partialState(GitLabMergeRequestsFiltersValue::author) {
    copy(author = it)
  }

  override val assigneeFilterState = searchState.partialState(GitLabMergeRequestsFiltersValue::assignee) {
    copy(assignee = it)
  }

  override val reviewerFilterState = searchState.partialState(GitLabMergeRequestsFiltersValue::reviewer) {
    copy(reviewer = it)
  }

  override val labelFilterState = searchState.partialState(GitLabMergeRequestsFiltersValue::label) {
    copy(label = it)
  }

  override suspend fun getLabels(): List<GitLabLabelDTO> = projectData.getLabels()

  override suspend fun getMergeRequestMembers(): List<GitLabMemberDTO> =
    projectData.getMembers().filter { isValidMergeRequestAccessLevel(it.accessLevel) }

  companion object {
    private fun isValidMergeRequestAccessLevel(accessLevel: GitLabAccessLevel): Boolean {
      return accessLevel == GitLabAccessLevel.REPORTER ||
             accessLevel == GitLabAccessLevel.DEVELOPER ||
             accessLevel == GitLabAccessLevel.MAINTAINER ||
             accessLevel == GitLabAccessLevel.OWNER
    }
  }
}