// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.rw

import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.executeWithChildJob
import com.intellij.openapi.progress.util.ProgressIndicatorUtils.runActionAndCancelBeforeWrite
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

internal fun <X> cancellableReadActionInternal(currentJob: Job, action: () -> X): X {
  return try {
    executeWithChildJob(currentJob) { readJob ->
      var resultRef: Value<X>? = null
      // A child Job is started to be externally cancellable by a write action without cancelling the current Job.
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
  catch (e: CancellationException) {
    throw e.cause as? CannotReadException // cancelled normally by a write action
          ?: e // exception from the computation
  }
}

private fun Job.cancelReadJob() {
  cancel(cause = CancellationException(CannotReadException()))
}

private class Value<T>(val value: T)
