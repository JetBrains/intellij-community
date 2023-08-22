// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import java.util.*
import javax.swing.ListModel

interface GHPRReviewThreadModel : ListModel<GHPRReviewCommentModel> {
  val id: String
  val createdAt: Date
  val state: GHPullRequestReviewCommentState
  val isResolved: Boolean
  val isOutdated: Boolean
  val commit: GHCommitHash?
  val filePath: String
  val patchHunk: PatchHunk?
  val diffHunk: String
  val originalLocation: DiffLineLocation?
  val originalStartLocation: DiffLineLocation?
  val location: DiffLineLocation?
  val startLocation: DiffLineLocation?

  val collapsedState: MutableStateFlow<Boolean>

  val repliesModel: ListModel<GHPRReviewCommentModel>

  fun update(thread: GHPullRequestReviewThread)
  fun addComment(comment: GHPullRequestReviewComment)

  fun addAndInvokeStateChangeListener(listener: () -> Unit)
  val originalCommit: GHCommitHash?
}
