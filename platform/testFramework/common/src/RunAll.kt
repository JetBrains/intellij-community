// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.common.runAllCatching
import com.intellij.util.ThrowableConsumer
import com.intellij.util.ThrowablePairConsumer
import com.intellij.util.ThrowableRunnable
import com.intellij.util.lang.CompoundRuntimeException
import org.jetbrains.annotations.TestOnly

/**
 * Runs all given runnables and throws all the caught exceptions at the end.
 */
@TestOnly
class RunAll(private val actions: List<ThrowableRunnable<*>>) : Runnable {
  @SafeVarargs
  constructor(vararg actions: ThrowableRunnable<Throwable?>) : this(listOf(*actions))

  override fun run() {
    actions.asSequence().actionSequence().runAll()
  }

  fun run(earlierExceptions: List<Throwable>? = null) {
    val actions = actions.asSequence().actionSequence()
    val throwable = actions.runAllCatching()
    if (earlierExceptions.isNullOrEmpty()) {
      throwable?.let {
        throw it
      }
    }
    else {
      val compound = CompoundRuntimeException(earlierExceptions)
      throwable?.let {
        compound.addSuppressed(it)
      }
      throw compound
    }
  }

  @TestOnly
  companion object {
    @JvmStatic // for usage from Java
    fun runAll(vararg actions: ThrowableRunnable<*>) {
      actions.asSequence().actionSequence().runAll()
    }

    @JvmStatic // for usage from Java
    fun <K, V> runAll(input: Map<out K?, V?>, action: ThrowablePairConsumer<in K?, in V?, Throwable?>) {
      if (input.isEmpty()) {
        return
      }
      input.entries.asSequence().map {
        { action.consume(it.key, it.value) }
      }.runAll()
    }

    @JvmStatic // for usage from Java
    fun <T> runAll(input: Collection<T>, action: ThrowableConsumer<in T, Throwable?>) {
      if (input.isEmpty()) {
        return
      }
      input.asSequence().map {
        {
          action.consume(it)
        }
      }.runAll()
    }

    private fun Sequence<ThrowableRunnable<*>>.actionSequence(): Sequence<() -> Unit> {
      return map {
        (it::run)
      }
    }
  }
}
