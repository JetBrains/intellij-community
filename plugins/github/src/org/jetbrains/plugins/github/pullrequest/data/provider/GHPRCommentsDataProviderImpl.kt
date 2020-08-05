// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.messages.MessageBus
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentService
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.concurrent.CompletableFuture

class GHPRCommentsDataProviderImpl(private val commentService: GHPRCommentService,
                                   private val pullRequestId: GHPRIdentifier,
                                   private val messageBus: MessageBus) : GHPRCommentsDataProvider {

  override fun addComment(progressIndicator: ProgressIndicator,
                          body: String): CompletableFuture<GithubIssueCommentWithHtml> =
    commentService.addComment(progressIndicator, pullRequestId, body).successOnEdt {
      messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onCommentAdded()
      it
    }

  override fun getCommentMarkdownBody(progressIndicator: ProgressIndicator, commentId: String) =
    commentService.getCommentMarkdownBody(progressIndicator, commentId)

  override fun updateComment(progressIndicator: ProgressIndicator, commentId: String, text: String): CompletableFuture<String> =
    commentService.updateComment(progressIndicator, commentId, text).successOnEdt {
      messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onCommentUpdated(commentId, it.bodyHTML)
      it
    }.thenApply { it.bodyHTML }

  override fun deleteComment(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<out Any?> =
    commentService.deleteComment(progressIndicator, commentId).successOnEdt {
      messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onCommentDeleted(commentId)
    }
}