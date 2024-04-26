// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.error

import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.swingAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal fun GitLabMergeRequestFilterErrorPresenter(
  filterVm: GitLabMergeRequestsFiltersViewModel
): ErrorStatusPresenter.Text<Throwable> = ErrorStatusPresenter.simple(
  GitLabBundle.message("merge.request.list.filter.error"),
  descriptionProvider = { null },
  actionProvider = {
    swingAction(GitLabBundle.message("merge.request.list.filter.error.action")) {
      filterVm.reloadData()
    }
  }
)