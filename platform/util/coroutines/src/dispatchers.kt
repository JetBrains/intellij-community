// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.platform.util.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

/**
 * Behaves like [CoroutineDispatcher.limitedParallelism], but adds "#[dispatcherName]" suffix to its string representation
 */
@ExperimentalCoroutinesApi
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