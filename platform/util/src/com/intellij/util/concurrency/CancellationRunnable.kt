// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import kotlinx.coroutines.CompletableJob
import java.util.concurrent.CancellationException

/**
 * A Runnable, which, when run, associates the calling thread with a job,
 * invokes original runnable, and completes the job.
 *
 * @see CancellationCallable
 */
internal class CancellationRunnable(private val myJob: CompletableJob, private val myRunnable: Runnable) : Runnable {
  override fun run() {
    try {
      myRunnable.run()
      myJob.complete()
    }
    catch (e: CancellationException) {
      myJob.completeExceptionally(e)
    }
    catch (e: Throwable) {
      myJob.completeExceptionally(e)
      throw e
    }
  }

  override fun toString(): String {
    return myRunnable.toString()
  }
}
