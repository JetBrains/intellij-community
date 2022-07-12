// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    runAll(actions.asSequence().map { it::run })
  }

  fun run(earlierExceptions: List<Throwable>? = null) {
    if (earlierExceptions == null) {
      runAll(actions.asSequence().map { it::run })
    }
    else {
      val exceptions: MutableList<Throwable> = ArrayList(earlierExceptions)
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
  }

  companion object {
    @JvmStatic
    fun runAll(vararg actions: ThrowableRunnable<*>) {
      runAll(actions.asSequence().map { it::run })
    }
  }
}

fun <K, V> runAll(input: Map<out K?, V?>, action: ThrowablePairConsumer<in K?, in V?, Throwable?>) {
  if (input.isNotEmpty()) {
    runAll(input.entries.asSequence().map { return@map { action.consume(it.key, it.value) } })
  }
}

fun <T> runAll(input: Collection<T>, action: ThrowableConsumer<in T, Throwable?>) {
  if (input.isNotEmpty()) {
    runAll(input.asSequence().map { return@map { action.consume(it) } })
  }
}

fun runAll(vararg actions: () -> Unit) {
  runAll(actions.asSequence())
}

fun runAll(actions: Sequence<() -> Unit>) {
  var exception: Throwable? = null
  for (action in actions) {
    try {
      action()
    }
    catch (e: Throwable) {
      if (exception == null) {
        exception = e
      }
      else if (e is CompoundRuntimeException) {
        e.exceptions.forEach(exception::addSuppressed)
      }
      else {
        exception.addSuppressed(e)
      }
    }
  }
  if (exception != null) {
    throw exception
  }
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