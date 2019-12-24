// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.NonReusableEmptyProgressIndicator
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates

abstract class GHListLoaderBase<T>(protected val progressManager: ProgressManager)
  : GHListLoader, Disposable {

  private var lastFuture = CompletableFuture.completedFuture(emptyList<T>())
  private var progressIndicator = NonReusableEmptyProgressIndicator()

  private val loadingStateChangeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)
  override var loading: Boolean by Delegates.observable(false) { _, _, _ ->
    loadingStateChangeEventDispatcher.multicaster.eventOccurred()
  }

  private val errorChangeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)
  override var error: Throwable? by Delegates.observable<Throwable?>(null) { _, _, _ ->
    errorChangeEventDispatcher.multicaster.eventOccurred()
  }

  override fun canLoadMore() = !loading && (error != null)

  override fun loadMore(update: Boolean) {
    val indicator = progressIndicator
    if (canLoadMore() || update) {
      loading = true
      requestLoadMore(indicator, update).handleOnEdt { list, error ->
        if (indicator.isCanceled) return@handleOnEdt
        loading = false
        when {
          error != null && !GithubAsyncUtil.isCancellation(error) -> {
            this.error = error
          }
          list != null -> {
            handleResult(list)
          }
        }
      }
    }
  }

  abstract fun handleResult(list: List<T>)

  private fun requestLoadMore(indicator: ProgressIndicator, update: Boolean): CompletableFuture<List<T>> {
    lastFuture = lastFuture.thenApplyAsync {
      progressManager.runProcess(Computable { doLoadMore(indicator, update) }, indicator)
    }
    return lastFuture
  }

  protected abstract fun doLoadMore(indicator: ProgressIndicator, update: Boolean): List<T>?

  override fun reset() {
    lastFuture = lastFuture.handle { _, _ ->
      listOf<T>()
    }

    progressIndicator.cancel()
    progressIndicator = NonReusableEmptyProgressIndicator()
    error = null
    loading = false
  }

  override fun addLoadingStateChangeListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(loadingStateChangeEventDispatcher, disposable, listener)

  override fun addErrorChangeListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(errorChangeEventDispatcher, disposable, listener)

  override fun dispose() = progressIndicator.cancel()
}