// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.common.runAllCatching
import com.intellij.util.ThrowableConsumer
import com.intellij.util.ThrowablePairConsumer
import com.intellij.util.ThrowableRunnable
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.throwIfNotEmpty

/**
 * Runs all given runnables and throws all the caught exceptions at the end.
 */
class RunAll(private val actions: List<ThrowableRunnable<*>>) : Runnable {

  constructor(vararg actions: ThrowableRunnable<Throwable?>) : this(listOf(*actions))

  override fun run() {
    actions.asSequence().actionSequence().runAll()
  }

  fun run(earlierExceptions: List<Throwable>? = null) {
    val actions: Sequence<() -> Unit> = actions.asSequence().actionSequence()
    if (earlierExceptions == null) {
      actions.runAll()
    }
    else {
      val exceptions: List<Throwable> = earlierExceptions + actions.runAllCatching()
      throwIfNotEmpty(exceptions)
    }
  }

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

    private fun Sequence<ThrowableRunnable<*>>.actionSequence(): Sequence<() -> Unit> = map {
      (it::run)
    }
  }
}

@Deprecated(
  "Moved to com.intellij.testFramework.RunAll",
  ReplaceWith("com.intellij.testFramework.RunAll.runAll(input, action)")
)
fun <K, V> runAll(input: Map<out K?, V?>, action: ThrowablePairConsumer<in K?, in V?, Throwable?>) {
  RunAll.runAll(input, action)
}

@Deprecated(
  "Moved to com.intellij.testFramework.RunAll",
  ReplaceWith("com.intellij.testFramework.RunAll.runAll(input, action)")
)
fun <T> runAll(input: Collection<T>, action: ThrowableConsumer<in T, Throwable?>) {
  RunAll.runAll(input, action)
}

@Deprecated(
  "Moved to com.intellij.testFramework.common",
  ReplaceWith("com.intellij.testFramework.common.runAll(*actions)"),
)
fun runAll(vararg actions: () -> Unit) {
  runAll(*actions)
}

@Deprecated(
  "Moved to com.intellij.testFramework.common",
  ReplaceWith("actions.runAll()", "com.intellij.testFramework.common.runAll"),
)
fun runAll(actions: Sequence<() -> Unit>) {
  actions.runAll()
}

@Deprecated("Use other runAll methods or com.intellij.testFramework.common.runAllCatching")
inline fun MutableList<Throwable>.catchAndStoreExceptions(executor: () -> Unit) {
  try {
    executor()
  }
  catch (e: CompoundRuntimeException) {
    addAll(e.exceptions)
  }
  catch (e: Throwable) {
    add(e)
  }
}
