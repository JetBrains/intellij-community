// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import org.jetbrains.plugins.github.api.data.GHComment
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

interface GHPRCommentService {
  suspend fun addComment(pullRequestId: GHPRIdentifier, body: String): GithubIssueCommentWithHtml

  suspend fun updateComment(commentId: String, text: String): GHComment

  suspend fun deleteComment(commentId: String)
}
