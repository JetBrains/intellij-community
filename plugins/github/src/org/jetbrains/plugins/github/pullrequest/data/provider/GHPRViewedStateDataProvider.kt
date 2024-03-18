// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestFileViewedState
import java.util.concurrent.CompletableFuture

interface GHPRViewedStateDataProvider {

  @RequiresEdt
  fun loadViewedState(): CompletableFuture<Map<String, GHPullRequestFileViewedState>>

  @RequiresEdt
  fun getViewedState(): Map<String, GHPullRequestFileViewedState>?

  @RequiresEdt
  fun updateViewedState(path: String, isViewed: Boolean)

  @RequiresEdt
  fun addViewedStateListener(parent: Disposable, listener: () -> Unit)

  @RequiresEdt
  fun reset()
}

fun GHPRViewedStateDataProvider.createViewedStateRequestsFlow(): Flow<CompletableFuture<Map<String, GHPullRequestFileViewedState>>> =
  callbackFlow {
    val disposable = Disposer.newDisposable()
    addViewedStateListener(disposable) {
      trySend(loadViewedState())
    }
    send(loadViewedState())
    awaitClose { Disposer.dispose(disposable) }
  }