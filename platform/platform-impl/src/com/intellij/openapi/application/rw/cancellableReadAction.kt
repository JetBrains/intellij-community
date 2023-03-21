// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.application.rw

import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.executeActionAndCompleteJob
import com.intellij.openapi.progress.prepareThreadContext
import com.intellij.openapi.progress.util.ProgressIndicatorUtils.runActionAndCancelBeforeWrite
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

internal fun <X> cancellableReadAction(action: () -> X): X = prepareThreadContext { ctx ->
  try {
    cancellableReadActionInternal(ctx, action)
  }
  catch (ce: CancellationException) {
    // One of two variants is thrown:
    // 1.
    // CannotReadException(
    //   CurrentJobCancellationException(JobCanceledException(
    //     CancellationException(original CannotReadException)
    //   ))
    // )
    // 2.
    // CannotReadException(
    //   CancellationException(original CannotReadException)
    // )
    val cause = Cancellation.getCause(ce)
    if (cause is CannotReadException) {
      // cancelled normally by a write action
      throw CannotReadException(ce)
    }
    throw ce // exception from the computation
  }
}

internal fun <X> cancellableReadActionInternal(ctx: CoroutineContext, action: () -> X): X {
  // A child Job is started to be externally cancellable by a write action without cancelling the current Job.
  val readJob = Job(parent = ctx[Job])
  return executeActionAndCompleteJob(readJob) {
    blockingContext(ctx + readJob) {
      var resultRef: Value<X>? = null
      val application = ApplicationManagerEx.getApplicationEx()
      runActionAndCancelBeforeWrite(application, readJob::cancelReadJob) {
        readJob.ensureActive()
        application.tryRunReadAction {
          readJob.ensureActive()
          resultRef = Value(action())
        }
      }
      val result = resultRef
      if (result == null) {
        readJob.ensureActive()
        error("read job must've been cancelled")
      }
      result.value
    }
  }
}

private fun Job.cancelReadJob() {
  cancel(cause = CancellationException(CannotReadException()))
}

private class Value<T>(val value: T)
