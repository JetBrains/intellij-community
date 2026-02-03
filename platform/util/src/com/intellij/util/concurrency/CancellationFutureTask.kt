// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import java.util.concurrent.CancellationException
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A FutureTask, which cancels the given job when it's cancelled.
 */
@OptIn(InternalCoroutinesApi::class)
internal class CancellationFutureTask<V>(
  private val job: Job,
  callable: ContextCallable<V>,
  val executionTracker: AtomicBoolean,
  val context: ChildContext,
) : FutureTask<V>(callable) {

  init {
    job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) {
      // Future is not tolerant to a manually thrown CancellationException
      // To properly handle the job-future interaction, we need to manually cancel future when the job is cancelled
      if (it is CancellationException) {
        cancel(false)
      }
    }
  }

  override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
    val isCurrentlyRunning = executionTracker.getAndSet(true)
    val result = super.cancel(mayInterruptIfRunning)
    job.cancel(null)
    if (!isCurrentlyRunning) {
      context.cancelAllIntelliJElements()
    }
    return result
  }
}
