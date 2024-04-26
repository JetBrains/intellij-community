// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.ProgressStep
import com.intellij.platform.util.progress.impl.ProgressStepImpl
import com.intellij.platform.util.progress.impl.ScopedLambda
import com.intellij.platform.util.progress.impl.StepConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * A pipe which has 2 ends: one for reporting progress, and another for reading the progress.
 */
@Experimental
sealed interface ProgressPipe {

  /**
   * Installs the reporting end of this pipe as [currentProgressStep] inside [action].
   * Progress, which was reported inside [action], is available as [progressUpdates].
   */
  suspend fun <T> collectProgressUpdates(action: ScopedLambda<T>): T

  /**
   * Infinite shared flow of progress updates.
   */
  fun progressUpdates(): Flow<ProgressState>
}

fun CoroutineScope.createProgressPipe(): ProgressPipe {
  // TODO consider exposing indeterminate and/or textLevel parameters
  return ProgressPipeImpl(ProgressStepImpl(this, StepConfig(isIndeterminate = false, textLevel = 2)))
}

private class ProgressPipeImpl(private val rootStep: ProgressStep) : ProgressPipe {

  override suspend fun <T> collectProgressUpdates(action: ScopedLambda<T>): T {
    return withContext(rootStep.asContextElement(), action)
  }

  override fun progressUpdates(): Flow<ProgressState> {
    return rootStep.progressUpdates()
  }
}
