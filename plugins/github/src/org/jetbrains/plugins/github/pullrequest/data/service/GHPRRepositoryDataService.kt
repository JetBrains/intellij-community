// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.util.CollectionDelta
import java.util.concurrent.CompletableFuture

interface GHPRRepositoryDataService : Disposable {
  val collaboratorsWithPushAccess: CompletableFuture<List<GHUser>>
  val teams: CompletableFuture<List<GHTeam>>
  val potentialReviewers: CompletableFuture<List<GHPullRequestRequestedReviewer>>
  val issuesAssignees: CompletableFuture<List<GHUser>>
  val labels: CompletableFuture<List<GHLabel>>

  @CalledInAwt
  fun resetData()
}