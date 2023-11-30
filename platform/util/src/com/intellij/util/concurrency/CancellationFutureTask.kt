// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import kotlinx.coroutines.Job
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask

/**
 * A FutureTask, which cancels the given job when it's cancelled.
 */
internal class CancellationFutureTask<V>(
  private val job: Job,
  callable: Callable<V>,
) : FutureTask<V>(callable) {

  override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
    val result = super.cancel(mayInterruptIfRunning)
    job.cancel(null)
    return result
  }
}
