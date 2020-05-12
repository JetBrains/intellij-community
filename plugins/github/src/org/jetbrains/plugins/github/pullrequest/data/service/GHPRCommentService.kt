// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import java.util.concurrent.CompletableFuture

interface GHPRCommentService {

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, body: String)
    : CompletableFuture<GithubIssueCommentWithHtml>
}
