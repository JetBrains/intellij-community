// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * Some clients of IntelliJ Platform (like Kotlin Compiler) do now want to depend on IJ's form of Kotlin coroutines
 * Therefore, we proxy [kotlinx.coroutines.internal.intellij.IntellijCoroutines] by introducing a set of no-op methods
 */
@ApiStatus.Internal
object IntelliJCoroutinesFacade {
  val canUseIntelliJCoroutines: Boolean = System.getProperty("ide.can.use.coroutines.fork", "true").toBoolean()

  fun currentThreadCoroutineContext(): CoroutineContext? {
    return if (canUseIntelliJCoroutines) {
      @OptIn(InternalCoroutinesApi::class)
      kotlinx.coroutines.internal.intellij.IntellijCoroutines.currentThreadCoroutineContext()
    } else {
      return null
    }
  }

  @Throws(InterruptedException::class)
  fun <T> runBlockingWithParallelismCompensation(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
  ): T {
    return if (canUseIntelliJCoroutines) {
      @OptIn(InternalCoroutinesApi::class)
      kotlinx.coroutines.internal.intellij.IntellijCoroutines.runBlockingWithParallelismCompensation(context, block)
    } else {
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking(context, block)
    }
  }

  fun <T> runAndCompensateParallelism(timeout: Duration, action: () -> T): T {
    return if (canUseIntelliJCoroutines) {
      @OptIn(InternalCoroutinesApi::class)
      kotlinx.coroutines.internal.intellij.IntellijCoroutines.runAndCompensateParallelism(timeout, action)
    } else {
      action()
    }
  }
}