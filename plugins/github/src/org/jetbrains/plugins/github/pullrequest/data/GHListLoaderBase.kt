// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.util.NonReusableEmptyProgressIndicator
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates

abstract class GHListLoaderBase<T>(protected val progressManager: ProgressManager)
  : GHListLoader<T> {

  private var lastFuture = CompletableFuture.completedFuture(emptyList<T>())
  private var progressIndicator = NonReusableEmptyProgressIndicator()

  private val loadingStateChangeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)
  override var loading: Boolean by Delegates.observable(false) { _, _, _ ->
    loadingStateChangeEventDispatcher.multicaster.eventOccurred()
  }

  private val errorChangeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)
  override var error: Throwable? by Delegates.observable(null) { _, _, _ ->
    errorChangeEventDispatcher.multicaster.eventOccurred()
  }

  private val dataEventDispatcher = EventDispatcher.create(GHListLoader.ListDataListener::class.java)
  override val loadedData = ArrayList<T>()

  override fun canLoadMore() = !loading && error == null

  override fun loadMore(update: Boolean) {
    if (Disposer.isDisposed(this)) return

    val indicator = progressIndicator
    if (canLoadMore() || update) {
      loading = true
      requestLoadMore(indicator, update).handleOnEdt { list, error ->
        if (indicator.isCanceled) return@handleOnEdt
        if (error != null) {
          if (!CompletableFutureUtil.isCancellation(error)) this.error = error
        }
        else if (!list.isNullOrEmpty()) {
          val startIdx = loadedData.size
          loadedData.addAll(list)
          dataEventDispatcher.multicaster.onDataAdded(startIdx)
        }
        loading = false
      }
    }
  }

  private fun requestLoadMore(indicator: ProgressIndicator, update: Boolean): CompletableFuture<List<T>> {
    lastFuture = lastFuture.thenCompose {
      progressManager.submitIOTask(indicator) {
        doLoadMore(indicator, update)
      }
    }
    return lastFuture
  }

  protected abstract fun doLoadMore(indicator: ProgressIndicator, update: Boolean): List<T>?

  override fun updateData(updater: (T) -> T?) {
    for (i in loadedData.indices) {
      val updatedValue = updater(loadedData[i]) ?: continue
      loadedData[i] = updatedValue
      dataEventDispatcher.multicaster.onDataUpdated(i)
      break
    }
  }

  override fun removeData(predicate: (T) -> Boolean) {
    val index = loadedData.indexOfFirst(predicate)
    if (index >= 0) {
      loadedData.removeAt(index)
      dataEventDispatcher.multicaster.onDataRemoved(index)
    }
  }

  override fun reset() {
    lastFuture = lastFuture.handle { _, _ ->
      listOf()
    }
    progressIndicator.cancel()
    progressIndicator = NonReusableEmptyProgressIndicator()
    error = null
    loading = false
    loadedData.clear()
    dataEventDispatcher.multicaster.onAllDataRemoved()
  }

  override fun addLoadingStateChangeListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(loadingStateChangeEventDispatcher, disposable, listener)

  override fun addDataListener(disposable: Disposable, listener: GHListLoader.ListDataListener) =
    dataEventDispatcher.addListener(listener, disposable)

  override fun addErrorChangeListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(errorChangeEventDispatcher, disposable, listener)

  override fun dispose() = progressIndicator.cancel()
}