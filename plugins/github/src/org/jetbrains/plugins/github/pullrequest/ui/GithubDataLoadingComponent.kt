// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.ui.components.panels.Wrapper
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates

internal abstract class GithubDataLoadingComponent<T> : Wrapper() {
  private var updateFuture by Delegates.observable<CompletableFuture<Unit>?>(null) { _, oldValue, _ ->
    oldValue?.cancel(true)
  }

  /**
   * This works because [handleOnEdt] basically forms a EDT-synchronized section and result/exception is acquired from [dataRequest] on EDT
   *
   * In pseudocode:
   * when (dataRequest.isDone) { runOnEdt { handler(getResult(), getException()) } }
   */
  @CalledInAwt
  fun loadAndShow(dataRequest: CompletableFuture<T>?) {
    updateFuture = dataRequest?.let {
      setBusy(true)
      it.handleOnEdt { result, error ->
        when {
          error != null && !GithubAsyncUtil.isCancellation(error) -> handleError(error)
          result != null -> handleResult(result)
        }
        setBusy(false)
      }
    }
  }

  @CalledInAwt
  fun reset() {
    updateFuture = null
    resetUI()
    setBusy(false)
  }

  protected abstract fun resetUI()
  protected abstract fun handleResult(result: T)
  protected abstract fun handleError(error: Throwable)
  protected abstract fun setBusy(busy: Boolean)
}