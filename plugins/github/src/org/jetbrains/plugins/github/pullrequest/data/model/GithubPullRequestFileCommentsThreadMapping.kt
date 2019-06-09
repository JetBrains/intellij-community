// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.model

import com.intellij.diff.util.Side
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml

class GithubPullRequestFileCommentsThreadMapping(val side: Side, val fileLine: Int,
                                                 comments: List<GithubPullRequestCommentWithHtml>) {
  val comments: List<GithubPullRequestCommentWithHtml> = comments.sortedBy { it.createdAt }
}