// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.application.rw

import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.prepareForInstallation
import com.intellij.openapi.progress.prepareThreadContext
import com.intellij.openapi.progress.util.ProgressIndicatorUtils.runActionAndCancelBeforeWrite
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

internal fun <X> cancellableReadAction(action: () -> X): X = prepareThreadContext { ctx ->
  cancellableReadActionInternal(ctx, action)
}

internal fun <X> cancellableReadActionInternal(ctx: CoroutineContext, action: () -> X): X {
  // A child Job is started to be externally cancellable by a write action without cancelling the current Job.
  val readJob = Job(parent = ctx[Job])
  return try {
    installThreadContext(ctx.prepareForInstallation() + readJob) {
      var resultRef: Value<X>? = null
      val application = ApplicationManagerEx.getApplicationEx()
      val cancellation = CannotReadException.jobCancellation(readJob)
      runActionAndCancelBeforeWrite(application, cancellation) {
        readJob.ensureActive()
        val tryResult = application.tryRunReadAction {
          readJob.ensureActive()
          resultRef = Value(action())
        }
        if (!tryResult) {
          cancellation.run()
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
  catch (ce: CancellationException) { // may be thrown by ProgressManager.checkCanceled() inside [action] and wrapped by `blockingContext`
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

private class Value<T>(val value: T)
