// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.intellij.platform.util.progress.ConcurrentProgressReporterHandle
import com.intellij.platform.util.progress.RawProgressReporterHandle
import com.intellij.platform.util.progress.SequentialProgressReporterHandle
import com.intellij.platform.util.progress.StepState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal data object EmptyProgressStep : ProgressStep {

  override fun progressUpdates(): Flow<StepState> = flow {
    emit(initialState)
    awaitCancellation()
  }

  override suspend fun <X> withText(text: ProgressText, action: suspend CoroutineScope.() -> X): X {
    return coroutineScope(action)
  }

  override fun asConcurrent(size: Int): ConcurrentProgressReporterHandle? = null

  override fun asSequential(size: Int): SequentialProgressReporterHandle? = null

  override fun asRaw(): RawProgressReporterHandle? = null
}
