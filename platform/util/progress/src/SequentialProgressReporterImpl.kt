// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.LOG
import com.intellij.platform.util.progress.impl.ProgressText
import com.intellij.platform.util.progress.impl.ScalingStep
import com.intellij.platform.util.progress.impl.ScopedLambda
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Implementation of user-facing [SequentialProgressReporter] and [SequentialProgressReporterHandle].
 */
internal class SequentialProgressReporterImpl(
  private val builder: ScalingStep,
) : SequentialProgressReporter,
    SequentialProgressReporterHandle {

  override val reporter: SequentialProgressReporter get() = this

  override fun close(): Unit = builder.cancel()

  private val _currentStepHandle: AtomicReference<ScalingStep.ChildHandle> = AtomicReference(null)

  override fun nextStep(endFraction: Int, text: ProgressText?) {
    nextStepImpl(endFraction, text)
  }

  override suspend fun <T> nextStep(endFraction: Int, text: ProgressText?, action: ScopedLambda<T>): T {
    val handle = nextStepImpl(endFraction, text)
    return if (handle != null) {
      handle.use {
        withContext(handle.step.asContextElement(), action)
      }
    }
    else {
      ignoreProgressReportingIn(action)
    }
  }

  private fun nextStepImpl(endFraction: Int, text: ProgressText?): ScalingStep.ChildHandle? {
    if (endFraction <= 0 || builder.size < endFraction) {
      LOG.error(IllegalArgumentException("End fraction must be in (0, ${builder.size}], got: $endFraction"))
      return sizedStepImpl(workSize = 0, text)
    }
    val startedWork = builder.allocatedWork
    if (endFraction <= startedWork) {
      LOG.error(IllegalArgumentException("New end fraction $endFraction must be greater than the previous fraction $startedWork"))
      return sizedStepImpl(workSize = 0, text)
    }
    return sizedStepImpl(endFraction - startedWork, text)
  }

  override fun sizedStep(workSize: Int, text: ProgressText?) {
    sizedStepImpl(workSize, text)
  }

  override suspend fun <T> sizedStep(workSize: Int, text: ProgressText?, action: ScopedLambda<T>): T {
    val handle = sizedStepImpl(workSize, text)
    return if (handle != null) {
      handle.use {
        withContext(handle.step.asContextElement(), action)
      }
    }
    else {
      ignoreProgressReportingIn(action)
    }
  }

  private fun sizedStepImpl(workSize: Int, text: ProgressText?): ScalingStep.ChildHandle? {
    _currentStepHandle.getAndSet(null)?.close() // finish previous
    val handle = builder.createChild(workSize, text)
    if (!_currentStepHandle.compareAndSet(null, handle)) {
      handle.close() // finish new one in case CAS fails
      LOG.error(IllegalStateException("Sequential reporter is used concurrently"))
      return null
    }
    return handle
  }
}
