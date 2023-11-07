// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
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