// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import java.util.concurrent.Callable
import kotlin.coroutines.Continuation

/**
 * A Callable, which, when called, associates the calling thread with a job,
 * invokes original callable, and completes the job with its result.
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
    return runAsCoroutine(continuation, completeOnFinish = true) {
      callable.call()
    }
  }
}
