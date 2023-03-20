// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@ApiStatus.Internal
class BackgroundRefresher<T>(name: String, parentDisposable: Disposable) {
  private val executor = AppExecutorUtil.createBoundedScheduledExecutorService(name, 1)
  private val requestLock: Lock = ReentrantLock()

  // Concurrent access is fully handled by requestLock
  private var currentIndicator = EmptyProgressIndicator()
  private var isDisposed = false
  private var currentTask: ScheduledFuture<*>? = null
  private var nextRefresh: Boolean = false
  private val promisesToFulfil = mutableListOf<AsyncPromise<T>>()

  init {
    Disposer.register(parentDisposable, Disposable {
      requestLock.withLock {
        isDisposed = true
      }

      executor.shutdownNow()
      currentIndicator.cancel()
      currentTask = null

      for (asyncPromise in collectPromises(force = true)) {
        asyncPromise.setError(AsyncPromise.CANCELED)
      }
    })
  }


  /**
   * Request refresh and return the result of the latest one.
   * Promise will be resolved only when there are no more pending refreshes upon finishing next refresh.
   * Current refresh may be canceled via progress indicator by the next refresh to do less obsolete computations.
   */
  fun requestRefresh(delayMillis: Int, block: Computable<T>): Promise<T> = requestLock.withLock {
    if (isDisposed) return AsyncPromise<T>().also { it.cancel(false) }

    // Cancel queued refresh (does not affect already running one)
    currentTask?.cancel(false)
    // Cancel any already running refresh to save time
    currentIndicator.cancel()

    val promise = AsyncPromise<T>()
    promisesToFulfil.add(promise)

    nextRefresh = true

    currentTask = executor.schedule(Runnable {
      val indicator = requestLock.withLock {
        nextRefresh = false
        currentTask = null

        val indicator = EmptyProgressIndicator()
        currentIndicator = indicator
        indicator
      }

      // If the indicator is cancelled means next request was queued or parentDisposable was terminated
      try {
        val value = ProgressManager.getInstance().runProcess(block, indicator)
        ProgressManager.checkCanceled()
        if (executor.isShutdown) {
          throw ProcessCanceledException()
        }

        for (asyncPromise in collectPromises(force = false)) {
          asyncPromise.setResult(value)
        }
      }
      catch (t: Throwable) {
        // Pass any exception even ProcessCancelledException
        // If PCE was initiated by next refresh, collectPromises will return an empty list
        // and promises will be intact until next refresh
        for (asyncPromise in collectPromises(force = false)) {
          asyncPromise.setError(t)
        }
      }
    }, delayMillis.toLong(), TimeUnit.MILLISECONDS)

    promise
  }

  private fun collectPromises(force: Boolean): List<AsyncPromise<T>> {
    requestLock.withLock {
      if (!force && (nextRefresh || isDisposed)) {
        // wait for the next refresh
        return emptyList()
      }
      else {
        val promises = promisesToFulfil.toList()
        promisesToFulfil.clear()
        return promises
      }
    }
  }
}