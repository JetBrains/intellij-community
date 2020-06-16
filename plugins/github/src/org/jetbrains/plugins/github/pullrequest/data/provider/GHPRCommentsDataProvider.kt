// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml
import java.util.concurrent.CompletableFuture

interface GHPRCommentsDataProvider {

  @CalledInAwt
  fun addComment(progressIndicator: ProgressIndicator, body: String)
    : CompletableFuture<GithubIssueCommentWithHtml>

  @CalledInAwt
  fun getCommentMarkdownBody(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<String>

  @CalledInAwt
  fun updateComment(progressIndicator: ProgressIndicator, commentId: String, text: String): CompletableFuture<String>

  @CalledInAwt
  fun deleteComment(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<out Any?>

}