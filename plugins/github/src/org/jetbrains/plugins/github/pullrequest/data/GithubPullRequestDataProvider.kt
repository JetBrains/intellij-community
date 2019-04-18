// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import git4idea.GitCommit
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GithubCommit
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.pullrequest.comment.model.GithubPullRequestFileCommentThread
import java.util.*
import java.util.concurrent.CompletableFuture

interface GithubPullRequestDataProvider : Disposable {
  val number: Long

  val detailsRequest: CompletableFuture<GithubPullRequestDetailedWithHtml>
  val branchFetchRequest: CompletableFuture<Unit>
  val apiCommitsRequest: CompletableFuture<List<GithubCommit>>
  val logCommitsRequest: CompletableFuture<List<GitCommit>>
  val filesCommentThreadsRequest: CompletableFuture<List<GithubPullRequestFileCommentThread>>

  fun addRequestsChangesListener(listener: RequestsChangedListener)
  fun removeRequestsChangesListener(listener: RequestsChangedListener)

  @CalledInAwt
  fun reloadDetails()

  @CalledInAwt
  fun reloadCommits()

  interface RequestsChangedListener : EventListener {
    fun detailsRequestChanged()
    fun commitsRequestChanged()
  }
}