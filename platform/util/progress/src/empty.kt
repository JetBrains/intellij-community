// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.ProgressText
import com.intellij.platform.util.progress.impl.ScopedLambda
import kotlinx.coroutines.coroutineScope

// These have to live in the same package to keep base interfaces sealed

internal data object EmptySequentialReporter : SequentialProgressReporter {
  override fun nextStep(endFraction: Int, text: ProgressText?): Unit = Unit
  override fun sizedStep(workSize: Int, text: ProgressText?): Unit = Unit
  override suspend fun <T> nextStep(endFraction: Int, text: ProgressText?, action: ScopedLambda<T>): T = coroutineScope(action)
  override suspend fun <T> sizedStep(workSize: Int, text: ProgressText?, action: ScopedLambda<T>): T = coroutineScope(action)
}

internal data object EmptyConcurrentReporter : ConcurrentProgressReporter {
  override suspend fun <T> sizedStep(workSize: Int, text: ProgressText?, action: ScopedLambda<T>): T = coroutineScope(action)
}

internal data object EmptyRawProgressReporter : RawProgressReporter

internal data object EmptySequentialProgressReporterHandle : SequentialProgressReporterHandle {
  override val reporter: SequentialProgressReporter get() = EmptySequentialReporter
  override fun close() {}
}

internal data object EmptyConcurrentProgressReporterHandle : ConcurrentProgressReporterHandle {
  override val reporter: ConcurrentProgressReporter get() = EmptyConcurrentReporter
  override fun close() {}
}

internal data object EmptyRawProgressReporterHandle : RawProgressReporterHandle {
  override val reporter: RawProgressReporter get() = EmptyRawProgressReporter
  override fun close() {}
}
