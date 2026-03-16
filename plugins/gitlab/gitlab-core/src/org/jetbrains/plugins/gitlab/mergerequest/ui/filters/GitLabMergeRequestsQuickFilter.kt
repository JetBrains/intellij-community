// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.ReviewListQuickFilter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestStateFilterValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestsMemberFilterValue.MergeRequestsAssigneeFilterValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestsMemberFilterValue.MergeRequestsAuthorFilterValue
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestsMemberFilterValue.MergeRequestsReviewerFilterValue

@ApiStatus.Internal
sealed class GitLabMergeRequestsQuickFilter : ReviewListQuickFilter<GitLabMergeRequestsFiltersValue> {
  class Open : GitLabMergeRequestsQuickFilter() {
    override val filter = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.OPENED)
  }

  class IncludeMyChanges(user: GitLabUserDTO) : GitLabMergeRequestsQuickFilter() {
    override val filter = GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.OPENED,
      author = MergeRequestsAuthorFilterValue(user.username, user.name)
    )
  }

  class AssignedToMe(user: GitLabUserDTO) : GitLabMergeRequestsQuickFilter() {
    override val filter = GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.OPENED,
      assignee = MergeRequestsAssigneeFilterValue(user.username, user.name)
    )
  }

  class NeedMyReview(user: GitLabUserDTO) : GitLabMergeRequestsQuickFilter() {
    override val filter = GitLabMergeRequestsFiltersValue(
      state = MergeRequestStateFilterValue.OPENED,
      reviewer = MergeRequestsReviewerFilterValue(user.username, user.name)
    )
  }

  class Closed : GitLabMergeRequestsQuickFilter() {
    override val filter = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.CLOSED)
  }
}