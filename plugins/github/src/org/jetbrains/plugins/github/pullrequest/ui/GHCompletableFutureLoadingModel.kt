// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates.observable

class GHCompletableFutureLoadingModel<T> : GHSimpleLoadingModel<T>() {
  override var loading: Boolean = false

  override var result: T? = null
  override var error: Throwable? = null

  //to cancel old callbacks
  private var updateFuture by observable<CompletableFuture<Unit>?>(null) { _, oldValue, _ ->
    oldValue?.cancel(true)
  }

  @set:CalledInAwt
  @get:CalledInAwt
  var future by observable<CompletableFuture<T>?>(null) { _, _, newValue ->
    if (newValue != null) load(newValue) else reset()
  }

  private fun load(future: CompletableFuture<T>) {
    loading = true
    eventDispatcher.multicaster.onLoadingStarted()
    updateFuture = future.let {
      it.handleOnEdt { result, error ->
        when {
          error != null && !GithubAsyncUtil.isCancellation(error) -> this.error = error
          result != null -> this.result = result
        }
        loading = false
        eventDispatcher.multicaster.onLoadingCompleted()
      }
    }
  }

  private fun reset() {
    updateFuture = null
    loading = false
    result = null
    error = null
    eventDispatcher.multicaster.onReset()
  }
}