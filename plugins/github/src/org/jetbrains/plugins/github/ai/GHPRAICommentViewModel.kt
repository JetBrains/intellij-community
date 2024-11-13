// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai

import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModel
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * VM representing and controlling an AI-generated comment.
 */
@ApiStatus.Internal
interface GHPRAICommentViewModel {
  val key: Any
  val textHtml: StateFlow<String>

  val isBusy: StateFlow<Boolean>

  val isVisible: StateFlow<Boolean>
  val location: DiffLineLocation?

  val commentsVm: StateFlow<GHPRAIReviewThreadCommentsViewModel?>

  fun startChat()

  /**
   * Create the actual comment on the server
   */
  fun accept()

  /**
   * Discard the comment
   */
  fun reject()
}

@ApiStatus.Internal
interface GHPRAIReviewThreadCommentsViewModel {
  val messages: SharedFlow<GHPRAIReviewThreadChatMessage>
  val newMessageVm: GHPRAIReviewThreadNewCommentViewModel

  fun destroy()
  fun summarize()
}

@ApiStatus.Internal
interface GHPRAIReviewThreadNewCommentViewModel : CodeReviewSubmittableTextViewModel {
  fun submit()
  fun summarize()
}

/**
 * Corresponds with a message in a thread on an AI-generated comment.
 *
 * @param isResponse if it's an AI-generated response.
 */
@ApiStatus.Internal
data class GHPRAIReviewThreadChatMessage(val message: String, val isResponse: Boolean)