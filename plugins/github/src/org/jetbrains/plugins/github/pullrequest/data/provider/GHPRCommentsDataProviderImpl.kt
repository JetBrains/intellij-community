// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.messages.MessageBus
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentService
import org.jetbrains.plugins.github.util.completionOnEdt
import java.util.concurrent.CompletableFuture

class GHPRCommentsDataProviderImpl(private val commentService: GHPRCommentService,
                                   private val pullRequestId: GHPRIdentifier,
                                   private val messageBus: MessageBus) : GHPRCommentsDataProvider {

  override fun addComment(progressIndicator: ProgressIndicator,
                          body: String): CompletableFuture<GithubIssueCommentWithHtml> =
    commentService.addComment(progressIndicator, pullRequestId, body).completionOnEdt {
      messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onCommentAdded()
    }
}