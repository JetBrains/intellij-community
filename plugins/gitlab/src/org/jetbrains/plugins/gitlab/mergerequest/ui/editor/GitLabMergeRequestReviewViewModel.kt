// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabReviewTab

class GitLabMergeRequestReviewViewModel internal constructor(
  mergeRequest: GitLabMergeRequest,
  private val projectVm: GitLabToolWindowProjectViewModel
) {
  val mergeRequestIid: String = mergeRequest.iid

  /**
   * Show merge request details in a standard view
   */
  fun showMergeRequest() {
    projectVm.showTab(GitLabReviewTab.ReviewSelected(mergeRequestIid))
    projectVm.twVm.activate()
  }
}