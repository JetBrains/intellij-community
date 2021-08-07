// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

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
    doRun(actions.asSequence(), null)
  }

  fun run(earlierExceptions: List<Throwable>? = null) {
    doRun(actions.asSequence(), earlierExceptions)
  }

  companion object {
    @JvmStatic
    fun runAll(vararg actions: ThrowableRunnable<*>) {
      doRun(actions.asSequence())
    }

    @JvmStatic
    fun <T> runAll(input: Collection<T>, action: ThrowableConsumer<in T, Throwable?>) {
      if (input.isNotEmpty()) {
        doRun(input.asSequence().map { ThrowableRunnable<Throwable> { action.consume(it) } }, null)
      }
    }

    @JvmStatic
    fun <K, V> runAll(input: Map<out K?, V?>, action: ThrowablePairConsumer<in K?, in V?, Throwable?>) {
      if (input.isNotEmpty()) {
        doRun(input.entries.asSequence().map { ThrowableRunnable<Throwable> { action.consume(it.key, it.value) } }, null)
      }
    }
  }
}

private fun doRun(actions: Sequence<ThrowableRunnable<*>>, earlierExceptions: List<Throwable>? = null) {
  val exceptions: MutableList<Throwable> = if (earlierExceptions == null) ArrayList() else ArrayList(earlierExceptions)
  for (action in actions) {
    try {
      action.run()
    }
    catch (e: CompoundRuntimeException) {
      exceptions.addAll(e.exceptions)
    }
    catch (e: Throwable) {
      exceptions.add(e)
    }
  }
  throwIfNotEmpty(exceptions)
}

fun runAll(vararg actions: () -> Unit) {
  doRun(actions.asSequence().map { ThrowableRunnable<Throwable> { it() } })
}

fun runAll(actions: Sequence<() -> Unit>) {
  doRun(actions.map { ThrowableRunnable<Throwable> { it() } })
}

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