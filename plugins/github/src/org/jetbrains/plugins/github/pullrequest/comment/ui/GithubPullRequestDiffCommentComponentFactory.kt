// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import javax.swing.JComponent

interface GithubPullRequestDiffCommentComponentFactory : Disposable {
  fun createComponent(commentsThreads: List<List<GithubPullRequestCommentWithHtml>>): JComponent
}