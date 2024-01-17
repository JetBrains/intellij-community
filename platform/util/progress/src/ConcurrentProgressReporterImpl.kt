// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.ProgressText
import com.intellij.platform.util.progress.impl.ScalingStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * Implementation of user-facing [ConcurrentProgressReporter] and [ConcurrentProgressReporterHandle].
 */
internal class ConcurrentProgressReporterImpl(
  private val builder: ScalingStep,
) : ConcurrentProgressReporter,
    ConcurrentProgressReporterHandle {

  override val reporter: ConcurrentProgressReporter get() = this

  override fun close(): Unit = builder.cancel()

  override suspend fun <X> sizedStep(workSize: Int, text: ProgressText?, action: suspend CoroutineScope.() -> X): X {
    return builder.createChild(workSize, text).use {
      withContext(it.step.asContextElement(), action)
    }
  }
}
