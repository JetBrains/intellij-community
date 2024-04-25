// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentService

class GHPRCommentsDataProviderImpl(private val commentService: GHPRCommentService,
                                   private val pullRequestId: GHPRIdentifier,
                                   private val messageBus: MessageBus) : GHPRCommentsDataProvider {

  //TODO: switch to GQL
  override suspend fun addComment(body: String): GithubIssueCommentWithHtml =
    commentService.addComment(pullRequestId, body).also {
      withContext(NonCancellable + Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onCommentAdded()
      }
    }

  override suspend fun updateComment(commentId: String, text: String): String =
    commentService.updateComment(commentId, text).also {
      withContext(NonCancellable + Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onCommentUpdated(commentId, it.body)
      }
    }.body

  override suspend fun deleteComment(commentId: String) =
    commentService.deleteComment(commentId).also {
      withContext(NonCancellable + Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onCommentDeleted(commentId)
      }
    }
}