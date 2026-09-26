// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.diff.action

import com.intellij.collaboration.ui.codereview.diff.action.CodeReviewDiscussionsToggleAction
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiscussionsViewModel
import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModel

internal class GitLabMergeRequestDiffReviewDiscussionsToggleAction : CodeReviewDiscussionsToggleAction() {
  override fun findViewModel(ctx: DataContext): CodeReviewDiscussionsViewModel? =
    ctx.getData(GitLabMergeRequestReviewViewModel.DATA_KEY)
}