// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.error

import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.swingAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

fun GitLabMergeRequestTimelineErrorStatusPresenter(
  mr: GitLabMergeRequestTimelineViewModel
): ErrorStatusPresenter.Text<Throwable> = ErrorStatusPresenter.simple(
  GitLabBundle.message("merge.request.timeline.error"),
  actionProvider = {
    swingAction(GitLabBundle.message("merge.request.reload")) {
      mr.reloadData()
    }
  }
)