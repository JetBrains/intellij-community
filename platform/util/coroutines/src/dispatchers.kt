// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.platform.util.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

/**
 * [CoroutineDispatcher] returned by [softLimitedParallelism] behaves almost as one returned by [limitedParallelism] but allows temporarily
 * exceeding the parallelism limit in case [parallelism compensation](https://github.com/JetBrains/intellij-deps-kotlinx.coroutines/blob/master/IntelliJ-patches.md#parallelism-compensation-for-coroutinedispatchers)
 * was requested (e.g., by [kotlinx.coroutines.runBlocking]).
 *
 * This extension throws [UnsupportedOperationException] if [this] does not support parallelism compensation.
 * * [Dispatchers.Default][kotlinx.coroutines.Dispatchers.Default] and [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO] support
 *  parallelism compensation.
 * * Result of [softLimitedParallelism] supports parallelism compensation.
 * * Result of [limitedParallelism] _does not_ support parallelism compensation.
 *
 * Be advised that not every call to [limitedParallelism] can be interchangeably replaced with [softLimitedParallelism], because parallelism
 * compensation breaks the contract of [kotlinx.coroutines.internal.LimitedDispatcher]. For example, it is likely that `.limitedParallelism(1)`
 * _should not_ be replaced by `.softLimitedParallelism(1)`.
 */
@ExperimentalCoroutinesApi
fun CoroutineDispatcher.softLimitedParallelism(parallelism: Int, name: String?): CoroutineDispatcher {
  @OptIn(InternalCoroutinesApi::class)
  with(kotlinx.coroutines.internal.intellij.IntellijCoroutines) {
    return softLimitedParallelism(parallelism, name)
  }
}

/**
 * Behaves like [CoroutineDispatcher.limitedParallelism], but adds "#[dispatcherName]" suffix to its string representation
 */
@ExperimentalCoroutinesApi
@Deprecated("Use `limitedParallelism` from Kotlin coroutines", ReplaceWith("limitedParallelism(parallelism, dispatcherName)"))
fun CoroutineDispatcher.limitedParallelism(parallelism: Int, dispatcherName: String): CoroutineDispatcher =
  limitedParallelism(parallelism).withName(dispatcherName)

// TODO maybe promote it to public
private fun CoroutineDispatcher.withName(name: String): CoroutineDispatcher = NamedDispatcher(this, name)

private class NamedDispatcher(private val dispatcher: CoroutineDispatcher, private val name: String) : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) = dispatcher.dispatch(context, block)

  @InternalCoroutinesApi
  override fun dispatchYield(context: CoroutineContext, block: Runnable) = dispatcher.dispatchYield(context, block)

  override fun isDispatchNeeded(context: CoroutineContext): Boolean = dispatcher.isDispatchNeeded(context)

  @ExperimentalCoroutinesApi
  override fun limitedParallelism(parallelism: Int): CoroutineDispatcher = dispatcher.limitedParallelism(parallelism)

  override fun toString(): String = "$dispatcher#$name"
}