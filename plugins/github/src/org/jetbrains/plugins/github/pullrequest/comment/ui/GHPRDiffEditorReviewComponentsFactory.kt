// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewServiceAdapter
import javax.swing.JComponent

interface GHPRDiffEditorReviewComponentsFactory {
  fun createThreadComponent(reviewService: GHPRReviewServiceAdapter, thread: GHPRReviewThreadModel): JComponent
  fun createCommentComponent(reviewService: GHPRReviewServiceAdapter, commitSha: String, path: String, diffLine: Int,
                             onSuccess: (GithubPullRequestCommentWithHtml) -> Unit): JComponent
}