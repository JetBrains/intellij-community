// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import kotlinx.coroutines.*
import java.lang.Runnable

/**
 * A Runnable, which, when run, associates the calling thread with a job,
 * invokes original runnable, and completes the job.
 *
 * @see CancellationCallable
 */
internal class CancellationRunnable(private val myJob: CompletableJob, private val myRunnable: Runnable) : Runnable {

  override fun run() {
    runAsCoroutine(myJob, myRunnable)
  }

  override fun toString(): String {
    return myRunnable.toString()
  }
}
