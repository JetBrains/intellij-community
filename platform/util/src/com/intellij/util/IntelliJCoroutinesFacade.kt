// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

private val LOG: Logger
  get() = logger<IntelliJCoroutinesFacade>()

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

  /**
   * Grants one extra unit of parallelism to [this] dispatcher for the duration of [block], and revokes it
   * once [block] returns or throws.
   *
   * [block] is an ordinary blocking function, not a suspend lambda: call this from a dedicated [Thread], never
   * from a coroutine running on [this] dispatcher (that would just consume the newly granted capacity on the
   * code that requested it).
   *
   * This extension can only be used on [Dispatchers.Default][kotlinx.coroutines.Dispatchers.Default],
   * [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO], and on what [softLimitedParallelism] has returned.
   * If [canUseIntelliJCoroutines] is false, [block] just runs with no parallelism granted.
   */
  fun CoroutineDispatcher.withGrantedParallelism(block: (Boolean) -> Unit) {
    if (!canUseIntelliJCoroutines) {
      block(false)
      return
    }
    var granted = false
    try {
      @OptIn(InternalCoroutinesApi::class)
      with(kotlinx.coroutines.internal.intellij.IntellijCoroutines) {
        granted = tryAdjustParallelism(+1) == 1
      }
      if (!granted) {
        LOG.warn("$this: failed to grant parallelism")
      }
      block(granted)
    }
    finally {
      if (granted) {
        val revoked = @OptIn(InternalCoroutinesApi::class)
        with(kotlinx.coroutines.internal.intellij.IntellijCoroutines) {
          tryAdjustParallelism(-1) == -1
        }
        if (!revoked) {
          LOG.error("$this: failed to revoke parallelism")
        }
      }
    }
  }
}