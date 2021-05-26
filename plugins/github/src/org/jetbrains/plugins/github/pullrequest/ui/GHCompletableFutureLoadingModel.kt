// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates.observable

class GHCompletableFutureLoadingModel<T>(parentDisposable: Disposable)
  : GHSimpleLoadingModel<T>(), Disposable {

  //to cancel old callbacks
  private var updateFuture by observable<CompletableFuture<Unit>?>(null) { _, oldValue, _ ->
    oldValue?.cancel(true)
  }

  init {
    Disposer.register(parentDisposable, this)
  }

  @set:RequiresEdt
  @get:RequiresEdt
  var future by observable<CompletableFuture<T>?>(null) { _, _, newValue ->
    if (Disposer.isDisposed(this)) return@observable
    if (newValue != null) load(newValue) else reset()
  }

  private fun load(future: CompletableFuture<T>) {
    error = null
    loading = true
    eventDispatcher.multicaster.onLoadingStarted()
    updateFuture = future.handleOnEdt(this) { result, error ->
      if (error != null && !GithubAsyncUtil.isCancellation(error)) {
        this.error = error
        resultAvailable = false
      }
      else {
        this.result = result
        resultAvailable = true
      }
      loading = false
      eventDispatcher.multicaster.onLoadingCompleted()
    }
  }

  private fun reset() {
    updateFuture = null
    loading = false
    result = null
    resultAvailable = false
    error = null
    eventDispatcher.multicaster.onReset()
  }

  override fun dispose() {
    future = null
  }
}