// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.google.common.graph.Graph
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import java.util.concurrent.CompletableFuture

interface GHPRChangesService {

  @CalledInAny
  fun fetch(progressIndicator: ProgressIndicator, refspec: String): CompletableFuture<Unit>

  @CalledInAny
  fun fetchBranch(progressIndicator: ProgressIndicator, branch: String): CompletableFuture<Unit>

  @CalledInAny
  fun loadCommitsFromApi(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier)
    : CompletableFuture<Pair<GHCommit, Graph<GHCommit>>>

  @CalledInAny
  fun loadCommitDiffs(progressIndicator: ProgressIndicator, baseRefOid: String, oid: String): CompletableFuture<Pair<String, String>>

  @CalledInAny
  fun loadMergeBaseOid(progressIndicator: ProgressIndicator, baseRefOid: String, headRefOid: String): CompletableFuture<String>

  @CalledInAny
  fun createChangesProvider(progressIndicator: ProgressIndicator, mergeBaseOid: String, commits: Pair<GHCommit, Graph<GHCommit>>)
    : CompletableFuture<GHPRChangesProvider>
}