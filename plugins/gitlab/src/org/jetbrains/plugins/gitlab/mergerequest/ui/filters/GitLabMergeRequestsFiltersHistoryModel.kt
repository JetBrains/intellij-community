// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.filters

import com.intellij.collaboration.ui.codereview.list.search.PersistingReviewListSearchHistoryModel

internal class GitLabMergeRequestsFiltersHistoryModel(
  private val persistentHistoryComponent: GitLabMergeRequestsPersistentFiltersHistory
) : PersistingReviewListSearchHistoryModel<GitLabMergeRequestsFiltersValue>() {
  override var lastFilter: GitLabMergeRequestsFiltersValue?
    get() = persistentHistoryComponent.lastFilter
    set(value) {
      persistentHistoryComponent.lastFilter = value
    }

  override var persistentHistory: List<GitLabMergeRequestsFiltersValue>
    get() = persistentHistoryComponent.history
    set(value) {
      persistentHistoryComponent.history = value
    }
}