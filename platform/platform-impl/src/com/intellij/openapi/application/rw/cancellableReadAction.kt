// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.openapi.application.rw

import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.ensureCurrentJob
import com.intellij.openapi.progress.executeWithJobAndCompleteIt
import com.intellij.openapi.progress.util.ProgressIndicatorUtils.runActionAndCancelBeforeWrite
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

internal fun <X> cancellableReadAction(action: () -> X): X = ensureCurrentJob { currentJob ->
  try {
    cancellableReadActionInternal(currentJob, action)
  }
  catch (e: CancellationException) {
    throw e.cause as? CannotReadException // cancelled normally by a write action
          ?: e // exception from the computation
  }
}

internal fun <X> cancellableReadActionInternal(currentJob: Job, action: () -> X): X {
  // A child Job is started to be externally cancellable by a write action without cancelling the current Job.
  val readJob = Job(parent = currentJob)
  var resultRef: Value<X>? = null
  val application = ApplicationManagerEx.getApplicationEx()
  runActionAndCancelBeforeWrite(application, readJob::cancelReadJob) {
    readJob.ensureActive()
    application.tryRunReadAction {
      readJob.ensureActive()
      resultRef = Value(executeWithJobAndCompleteIt(readJob, action))
    }
  }
  val result = resultRef
  if (result == null) {
    readJob.ensureActive()
    error("read job must've been cancelled")
  }
  return result.value
}

private fun Job.cancelReadJob() {
  cancel(cause = CancellationException(CannotReadException()))
}

private class Value<T>(val value: T)
