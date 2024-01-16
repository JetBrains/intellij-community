// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewResolvableItemViewModel
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRNewThreadCommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewThreadCommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewThreadViewModel

interface GHPRReviewThreadEditorViewModel : GHPRReviewThreadViewModel,
                                            CodeReviewResolvableItemViewModel {
  override val isBusy: StateFlow<Boolean>

  val comments: StateFlow<List<CommentItem>>

  override val canCreateReplies: StateFlow<Boolean>
  val isWritingReply: StateFlow<Boolean>
  override val newReplyVm: GHPRNewThreadCommentViewModel

  fun startWritingReply()
  fun stopWritingReply()

  override val isResolved: StateFlow<Boolean>
  override val canChangeResolvedState: StateFlow<Boolean>
  override fun changeResolvedState()

  sealed interface CommentItem {
    data class Comment(val vm: GHPRReviewThreadCommentViewModel) : CommentItem
    data class Expander(val collapsedCount: Int, val expand: () -> Unit) : CommentItem
  }
}