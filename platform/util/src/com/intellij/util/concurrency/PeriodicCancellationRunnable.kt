// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import kotlinx.coroutines.job
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation

internal class PeriodicCancellationRunnable(
  private val continuation: Continuation<Unit>,
  private val runnable: Runnable,
) : Runnable {

  override fun run() {
    // don't complete the job, it can be either failed, or cancelled
    try {
      runAsCoroutine(continuation, completeOnFinish = false, runnable::run)
    }
    catch (e: CancellationException) {
      // According to the specification of the FutureTask, the runnable should not throw in case of cancellation.
      // Instead, Java relies on interruptions
      // This does not go along with the coroutines framework rules, but we have to play Java rules here
      if (!continuation.context.job.isCancelled) {
        throw e
      }
    }
  }
}
