// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.error

import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.swingAction
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.Action

class GitLabMergeRequestTimelineErrorStatusPresenter(private val mr: GitLabMergeRequestTimelineViewModel) : ErrorStatusPresenter<Throwable> {
  override fun getErrorTitle(error: Throwable): @Nls String = GitLabBundle.message("merge.request.timeline.error")

  override fun getErrorDescription(error: Throwable): @Nls String? =
    error.localizedMessage

  override fun getErrorAction(error: Throwable): Action =
    swingAction(GitLabBundle.message("merge.request.reload")) {
      mr.reloadData()
    }
}