// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.ui.components.panels.Wrapper
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture

internal abstract class GithubDataLoadingComponent<T> : Wrapper() {
  private var updateFuture: CompletableFuture<Unit>? = null

  /**
   * This works because [handleOnEdt] basically forms a EDT-synchronized section and result/exception is acquired from [dataRequest] on EDT
   *
   * In pseudocode:
   * when (dataRequest.isDone) { runOnEdt { handler(getResult(), getException()) } }
   */
  @CalledInAwt
  fun loadAndShow(dataRequest: CompletableFuture<T>?) {
    updateFuture?.cancel(true)
    reset()

    if (dataRequest == null) {
      updateFuture = null
      setBusy(false)
      return
    }

    setBusy(true)
    updateFuture = dataRequest.handleOnEdt { result, error ->
      when {
        error != null && !GithubAsyncUtil.isCancellation(error) -> handleError(error)
        result != null -> handleResult(result)
      }
      setBusy(false)
    }
  }

  protected abstract fun reset()
  protected abstract fun handleResult(result: T)
  protected abstract fun handleError(error: Throwable)
  protected abstract fun setBusy(busy: Boolean)
}