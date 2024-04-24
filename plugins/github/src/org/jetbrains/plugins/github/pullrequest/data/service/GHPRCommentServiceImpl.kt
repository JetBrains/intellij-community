// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.util.ResultUtil.processErrorAndGet
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.GHComment
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

private val LOG = logger<GHPRCommentService>()

class GHPRCommentServiceImpl(private val requestExecutor: GithubApiRequestExecutor,
                             private val repository: GHRepositoryCoordinates) : GHPRCommentService {

  override suspend fun addComment(pullRequestId: GHPRIdentifier, body: String): GithubIssueCommentWithHtml =
    runCatching {
      val comment = requestExecutor.executeSuspend(
        GithubApiRequests.Repos.Issues.Comments.create(repository, pullRequestId.number, body))
      comment
    }.processErrorAndGet {
      LOG.info("Error occurred while adding PR comment", it)
    }

  override suspend fun updateComment(commentId: String, text: String): GHComment =
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.Comment.updateComment(repository.serverPath, commentId, text))
    }.processErrorAndGet {
      LOG.info("Error occurred while updating comment", it)
    }

  override suspend fun deleteComment(commentId: String) {
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.Comment.deleteComment(repository.serverPath, commentId))
    }.processErrorAndGet {
      LOG.info("Error occurred while deleting comment", it)
    }
  }
}