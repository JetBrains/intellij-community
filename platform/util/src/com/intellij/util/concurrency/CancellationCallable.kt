// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.openapi.progress.CeProcessCanceledException
import com.intellij.openapi.progress.PceCancellationException
import com.intellij.openapi.progress.ProcessCanceledException
import java.util.concurrent.Callable
import kotlin.coroutines.Continuation

/**
 * A Callable, which, when called, associates the calling thread with a job,
 * invokes original callable, and completes the job with its result.
 *
 * This callable is intended to be invoked _within futures_, so it transforms ProcessCancelledException to
 * CancellationException
 *
 * @see CancellationFutureTask
 *
 * @see CancellationRunnable
 */
internal class CancellationCallable<V>(
  private val continuation: Continuation<Unit>,
  private val callable: Callable<out V>,
) : Callable<V> {

  override fun call(): V {
    try {
      return runAsCoroutine(continuation, completeOnFinish = true) {
        callable.call()
      }
    } catch (e : CeProcessCanceledException) {
      throw e.cause
    } catch (e : ProcessCanceledException) {
      throw PceCancellationException(e)
    }
  }
}
