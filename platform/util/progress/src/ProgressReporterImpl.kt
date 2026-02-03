// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.ProgressText
import com.intellij.platform.util.progress.impl.ScalingStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * Implementation of user-facing [ProgressReporter] and [ProgressReporterHandle].
 */
internal class ProgressReporterImpl(
  private val builder: ScalingStep,
) : ProgressReporter,
    ProgressReporterHandle {

  override val reporter: ProgressReporter get() = this

  override fun close(): Unit = builder.cancel()

  override suspend fun <X> sizedStep(workSize: Int, text: ProgressText?, action: suspend CoroutineScope.() -> X): X {
    return builder.createChild(workSize, text).use {
      withContext(it.step.asContextElement(), action)
    }
  }
}
