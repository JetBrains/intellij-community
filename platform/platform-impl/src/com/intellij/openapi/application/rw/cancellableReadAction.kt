// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.application.rw

import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.prepareThreadContext
import com.intellij.openapi.progress.util.ProgressIndicatorUtils.runActionAndCancelBeforeWrite
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

internal fun <X> cancellableReadAction(action: () -> X): X = prepareThreadContext { ctx ->
  try {
    cancellableReadActionInternal(ctx, action)
  }
  catch (readCe: ReadCancellationException) {
    throw CannotReadException(readCe)
  }
  catch (ce: CancellationException) {
    // One of two variants is thrown:
    // 1.
    // CannotReadException(
    //   CurrentJobCancellationException(JobCanceledException(
    //     ReadCancellationException()
    //   ))
    // )
    // 2.
    // CannotReadException(
    //   ReadCancellationException()
    // )
    val original = Cancellation.unwrap(ce)
    if (original is ReadCancellationException) {
      // cancelled normally by a write action
      throw CannotReadException(ce)
    }
    throw ce // exception from the computation
  }
}

internal fun <X> cancellableReadActionInternal(ctx: CoroutineContext, action: () -> X): X {
  // A child Job is started to be externally cancellable by a write action without cancelling the current Job.
  val readJob = Job(parent = ctx[Job])
  return try {
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
      readJob.complete()
      result.value
    }
  }
  catch (ce: CancellationException) {
    readJob.cancel(ce)
    throw ce
  }
  catch (e: Throwable) {
    // `job.completeExceptionally(e)` will fail parent Job,
    // which is not desired when this Job is a read action Job.
    //
    // ReadAction.computeCancellable {
    //   throw X
    // }
    // X will be re-thrown, but the caller is not expected to become cancelled
    // since it might catch X and act accordingly.
    //
    // Ideally, completeExceptionally should be used because it's more correct semantically,
    // but read job must not fail its parent regardless of whether the parent is supervisor:
    // https://github.com/Kotlin/kotlinx.coroutines/issues/3409
    readJob.cancel(CancellationException(e))
    throw e
  }
}

private fun Job.cancelReadJob() {
  cancel(cause = ReadCancellationException(cause = null))
}

private class Value<T>(val value: T)

@Internal
internal class ReadCancellationException(cause: Throwable?) : CancellationException() {
  init {
    if (cause != null) {
      initCause(cause)
    }
  }
}
