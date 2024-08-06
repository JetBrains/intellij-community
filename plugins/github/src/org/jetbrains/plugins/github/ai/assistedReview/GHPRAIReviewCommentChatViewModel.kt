// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import kotlinx.coroutines.flow.SharedFlow

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

data class GHPRAIReviewCommentChatMessage(val message: String, val isResponse: Boolean)