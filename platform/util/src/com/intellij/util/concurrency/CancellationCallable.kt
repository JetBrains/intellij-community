// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import kotlinx.coroutines.CompletableJob
import java.util.concurrent.Callable

/**
 * A Callable, which, when called, associates the calling thread with a job,
 * invokes original callable, and completes the job with its result.
 *
 * @see CancellationFutureTask
 *
 * @see CancellationRunnable
 */
internal class CancellationCallable<V>(
  private val job: CompletableJob,
  private val callable: Callable<out V>,
) : Callable<V> {

  override fun call(): V {
    return runAsCoroutine(job, completeOnFinish = true) {
      callable.call()
    }
  }
}
