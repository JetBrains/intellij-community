// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import javax.swing.JComponent

interface GHPRDiffEditorReviewComponentsFactory {
  fun createThreadComponent(thread: GHPRReviewThreadModel): JComponent
  fun createCommentComponent(diffLine: Int, onSuccess: (GithubPullRequestCommentWithHtml) -> Unit): JComponent
}