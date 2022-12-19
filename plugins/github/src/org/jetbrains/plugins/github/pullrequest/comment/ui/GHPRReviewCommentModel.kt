// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import java.util.*

interface GHPRReviewCommentModel {
  val id: String
  val canBeDeleted: Boolean
  val canBeUpdated: Boolean
  val state: GHPullRequestReviewCommentState
  val dateCreated: Date
  val body: String
  val author: GHActor?
  var isFirstInResolvedThread: Boolean

  fun update(comment: GHPullRequestReviewComment): Boolean
  fun addAndInvokeChangesListener(listener: () -> Unit)
}