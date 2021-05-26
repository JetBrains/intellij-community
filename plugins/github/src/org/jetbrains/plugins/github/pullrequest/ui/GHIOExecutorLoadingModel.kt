// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.util.*
import java.util.concurrent.CompletableFuture

class GHIOExecutorLoadingModel<T>(parentDisposable: Disposable)
  : GHSimpleLoadingModel<T>(), Disposable {

  private var currentProgressIndicator: ProgressIndicator? = null

  init {
    Disposer.register(parentDisposable, this)
  }

  @RequiresEdt
  fun load(progressIndicator: ProgressIndicator, task: (ProgressIndicator) -> T): CompletableFuture<T> {
    if (Disposer.isDisposed(this)) return CompletableFuture.failedFuture(ProcessCanceledException())

    currentProgressIndicator?.cancel()
    currentProgressIndicator = progressIndicator
    error = null
    loading = true
    eventDispatcher.multicaster.onLoadingStarted()

    return ProgressManager.getInstance()
      .submitIOTask(progressIndicator, task)
      .successOnEdt {
        if (progressIndicator.isCanceled) return@successOnEdt it
        result = it
        resultAvailable = true
        it
      }.errorOnEdt {
        if (progressIndicator.isCanceled || GithubAsyncUtil.isCancellation(it)) return@errorOnEdt
        error = it
        resultAvailable = false
      }.completionOnEdt {
        if (progressIndicator.isCanceled) return@completionOnEdt
        loading = false
        currentProgressIndicator = null
        eventDispatcher.multicaster.onLoadingCompleted()
      }
  }

  @RequiresEdt
  fun reset() {
    currentProgressIndicator?.cancel()
    currentProgressIndicator = null
    loading = false
    result = null
    resultAvailable = false
    error = null
    eventDispatcher.multicaster.onReset()
  }

  override fun dispose() {
    reset()
  }
}