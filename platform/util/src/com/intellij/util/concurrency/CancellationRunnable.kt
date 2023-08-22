// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import kotlinx.coroutines.Runnable
import kotlin.coroutines.Continuation

/**
 * A Runnable, which, when run, associates the calling thread with a job,
 * invokes original runnable, and completes the job.
 *
 * @see CancellationCallable
 */
internal class CancellationRunnable(
  private val continuation: Continuation<Unit>,
  private val runnable: Runnable,
) : Runnable {

  override fun run() {
    runAsCoroutine(continuation, completeOnFinish = true, runnable::run)
  }

  override fun toString(): String {
    return runnable.toString()
  }
}
