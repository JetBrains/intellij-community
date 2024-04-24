// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml

interface GHPRCommentsDataProvider {
  suspend fun addComment(body: String): GithubIssueCommentWithHtml

  suspend fun updateComment(commentId: String, text: String): @NlsSafe String

  suspend fun deleteComment(commentId: String)
}