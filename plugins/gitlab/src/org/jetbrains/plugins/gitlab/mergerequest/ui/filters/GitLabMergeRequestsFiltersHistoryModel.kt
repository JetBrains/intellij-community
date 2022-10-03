// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.PersistingReviewListSearchHistoryModel

internal class GitLabMergeRequestsFiltersHistoryModel : PersistingReviewListSearchHistoryModel<GitLabMergeRequestsFiltersValue>() {

  override var persistentHistory: List<GitLabMergeRequestsFiltersValue> = emptyList()

  override var lastFilter: GitLabMergeRequestsFiltersValue? = null
}