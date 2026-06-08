// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelViewModel
import com.intellij.collaboration.ui.codereview.list.search.ReviewListSearchPanelViewModelBase
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.collaboration.util.collectIncrementallyTo
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabLabel
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestStateFilterValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestsMemberFilterValue
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

@ApiStatus.Internal
interface GitLabMergeRequestsFiltersViewModel : ReviewListSearchPanelViewModel<GitLabMergeRequestsFiltersValue, GitLabMergeRequestsQuickFilter> {
  val currentUser: GitLabUserDTO
  val avatarIconsProvider: IconsProvider<GitLabUserDTO>

  val stateFilterState: MutableStateFlow<MergeRequestStateFilterValue?>
  val authorFilterState: MutableStateFlow<MergeRequestsMemberFilterValue?>
  val assigneeFilterState: MutableStateFlow<MergeRequestsMemberFilterValue?>
  val reviewerFilterState: MutableStateFlow<MergeRequestsMemberFilterValue?>
  val labelFilterState: MutableStateFlow<GitLabMergeRequestsFiltersValue.LabelFilterValue?>

  val mergeRequestMembers: StateFlow<IncrementallyComputedValue<List<GitLabUserDTO>>>
  val labels: StateFlow<IncrementallyComputedValue<List<GitLabLabel>>>

  fun reloadData()
}

@OptIn(FlowPreview::class)
internal class GitLabMergeRequestsFiltersViewModelImpl(
  scope: CoroutineScope,
  private val project: Project?, // only for statistics
  historyModel: GitLabMergeRequestsFiltersHistoryModel,
  override val currentUser: GitLabUserDTO,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  private val projectData: GitLabProject
) : GitLabMergeRequestsFiltersViewModel,
    ReviewListSearchPanelViewModelBase<GitLabMergeRequestsFiltersValue, GitLabMergeRequestsQuickFilter>(
      scope,
      historyModel,
      emptySearch = GitLabMergeRequestsFiltersValue.EMPTY,
      defaultFilter = defaultQuickFilter(currentUser).filter
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

  @OptIn(ExperimentalCoroutinesApi::class)
  override val mergeRequestMembers: StateFlow<IncrementallyComputedValue<List<GitLabUserDTO>>> =
    projectData.dataReloadSignal.withInitial(Unit).transformLatest {
      projectData.getMembersBatches().collectIncrementallyTo(this)
    }.stateIn(scope, SharingStarted.Lazily, IncrementallyComputedValue.loading())

  @OptIn(ExperimentalCoroutinesApi::class)
  override val labels: StateFlow<IncrementallyComputedValue<List<GitLabLabel>>> =
    projectData.dataReloadSignal.withInitial(Unit).transformLatest {
      projectData.getLabelsBatches().collectIncrementallyTo(this)
    }.stateIn(scope, SharingStarted.Lazily, IncrementallyComputedValue.loading())

  override fun reloadData() {
    projectData.reloadData()
  }

  init {
    scope.launchNow {
      // with debounce to avoid collecting intermediate state
      searchState.drop(1).debounce(5000).collect {
        if (it.filterCount > 0) {
          GitLabStatistics.logMrFiltersApplied(project, it)
        }
      }
    }
  }

  companion object {
    fun defaultQuickFilter(user: GitLabUserDTO): GitLabMergeRequestsQuickFilter = GitLabMergeRequestsQuickFilter.AssignedToMe(user)
  }
}