// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.model

import com.intellij.diff.util.Side
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.containers.SortedList
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import java.util.*
import kotlin.Comparator

class GithubPullRequestFileCommentThread(val change: Change, val side: Side, val fileLine: Int,
                                         comments: List<GithubPullRequestCommentWithHtml>) {

  val comments: List<GithubPullRequestCommentWithHtml>
    get() = sortedComments

  private val sortedComments =
    SortedList<GithubPullRequestCommentWithHtml>(Comparator.comparing<GithubPullRequestCommentWithHtml, Date> { it.createdAt }).apply {
      addAll(comments)
    }
}