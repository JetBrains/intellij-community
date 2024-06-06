// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import kotlinx.coroutines.flow.SharedFlow

interface GHPRAICommentChat {
  /**
   * Should emit both my messages and chat responses (in MD)
   */
  val messages: SharedFlow<GHPRAICommentChatMessage>

  /**
   * Sends the message to the chat
   */
  suspend fun sendMessage(message: String)

  /**
   * Summarize the discussion into a comment for the developer
   */
  suspend fun summarizeDiscussion()
}

data class GHPRAICommentChatMessage(val message: String, val isResponse: Boolean)
