// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.data.GHBranchProtectionRules
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import java.util.concurrent.CompletableFuture

interface GHPRStateService {

  @CalledInAny
  fun loadBranchProtectionRules(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, baseBranch: String)
    : CompletableFuture<GHBranchProtectionRules?>

  @CalledInAny
  fun loadMergeabilityState(progressIndicator: ProgressIndicator,
                            pullRequestId: GHPRIdentifier,
                            headRefOid: String,
                            prHtmlUrl: String,
                            baseBranchProtectionRules: GHBranchProtectionRules?): CompletableFuture<GHPRMergeabilityState>


  @CalledInAny
  fun close(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier): CompletableFuture<Unit>

  @CalledInAny
  fun reopen(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier): CompletableFuture<Unit>

  @CalledInAny
  fun markReadyForReview(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier): CompletableFuture<Unit>

  @CalledInAny
  fun merge(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier,
            commitMessage: Pair<String, String>, currentHeadRef: String): CompletableFuture<Unit>

  @CalledInAny
  fun rebaseMerge(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier,
                  currentHeadRef: String): CompletableFuture<Unit>

  @CalledInAny
  fun squashMerge(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier,
                  commitMessage: Pair<String, String>, currentHeadRef: String): CompletableFuture<Unit>
}
