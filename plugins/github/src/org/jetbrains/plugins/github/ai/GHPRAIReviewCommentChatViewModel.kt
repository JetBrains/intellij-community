// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai

import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GHPRAIReviewCommentChatViewModel {
  /**
   * Should emit both my messages and chat responses (in MD)
   */
  val messages: SharedFlow<GHPRAIReviewCommentChatMessage>

  /**
   * Sends the message to the chat
   */
  suspend fun sendMessage(message: String)

  /**
   * Summarize the discussion into a comment for the developer
   */
  suspend fun summarizeDiscussion()
}

@ApiStatus.Internal
data class GHPRAIReviewCommentChatMessage(val message: String, val isResponse: Boolean)