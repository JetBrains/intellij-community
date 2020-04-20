// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GHCommit
import java.util.*
import java.util.concurrent.CompletableFuture

interface GHPRDataProvider : GHPRTimelineLoaderHolder, Disposable {
  val id: GHPRIdentifier

  val headBranchFetchRequest: CompletableFuture<Unit>
  val apiCommitsRequest: CompletableFuture<List<GHCommit>>
  val changesProviderRequest: CompletableFuture<out GHPRChangesProvider>

  val detailsData: GHPRDetailsDataProvider
  val stateData: GHPRStateDataProvider
  val commentsData: GHPRCommentsDataProvider
  val reviewData: GHPRReviewDataProvider

  fun addRequestsChangesListener(listener: RequestsChangedListener)
  fun addRequestsChangesListener(disposable: Disposable, listener: RequestsChangedListener)
  fun removeRequestsChangesListener(listener: RequestsChangedListener)

  @CalledInAwt
  fun reloadChanges()

  interface RequestsChangedListener : EventListener {
    fun commitsRequestChanged() {}
  }
}