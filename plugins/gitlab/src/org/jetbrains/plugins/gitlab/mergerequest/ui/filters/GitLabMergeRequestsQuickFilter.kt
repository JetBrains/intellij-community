// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.ReviewListQuickFilter
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue.MergeRequestStateFilterValue

internal sealed class GitLabMergeRequestsQuickFilter : ReviewListQuickFilter<GitLabMergeRequestsFiltersValue> {
  class Open : GitLabMergeRequestsQuickFilter() {
    override val filter = GitLabMergeRequestsFiltersValue(state = MergeRequestStateFilterValue.OPENED)
  }
}