// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.util.Couple
import com.intellij.openapi.vcs.changes.Change
import git4idea.GitCommit
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import java.util.concurrent.CompletableFuture

interface GithubPullRequestDataProvider {
  val detailsRequest: CompletableFuture<GithubPullRequestDetailedWithHtml>
  val branchFetchRequest: CompletableFuture<Couple<String>>
  val logCommitsRequest: CompletableFuture<List<GitCommit>>
  val changesRequest: CompletableFuture<List<Change>>
}