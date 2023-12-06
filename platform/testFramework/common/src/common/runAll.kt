// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.util.lang.CompoundRuntimeException
import org.jetbrains.annotations.TestOnly

@TestOnly
fun runAll(vararg actions: () -> Unit) {
  actions.asSequence().runAllCatching()?.let {
    throw it
  }
}

@TestOnly
fun runAll(actions: Collection<() -> Unit>) {
  actions.asSequence().runAllCatching()?.let {
    throw it
  }
}

@TestOnly
fun <X> runAll(items: Collection<X>, action: (X) -> Unit) {
  items.asSequence().map {
    {
      action(it)
    }
  }.runAll()
}

@TestOnly
fun Sequence<() -> Unit>.runAll() {
  runAllCatching()?.let {
    throw it
  }
}

@TestOnly
fun runAllCatching(vararg actions: () -> Unit): Throwable? {
  return actions.asSequence().runAllCatching()
}

@TestOnly
fun <X> runAllCatching(items: Collection<X>, action: (X) -> Unit): Throwable? {
  return items.asSequence().map {
    {
      action(it)
    }
  }.runAllCatching()
}

@TestOnly
fun Sequence<() -> Unit>.runAllCatching(): Throwable? {
  var exception: Throwable? = null
  for (action in this) {
    try {
      action()
    }
    catch (e: CompoundRuntimeException) {
      if (exception == null) {
        exception = e
      }
      else {
        e.exceptions.forEach(exception::addSuppressed)
      }
    }
    catch (e: Throwable) {
      if (exception == null) {
        exception = e
      }
      else {
        exception.addSuppressed(e)
      }
    }
  }
  return exception
}

fun List<Throwable>.reduceAndThrow() {
  if (isEmpty()) {
    return
  }
  throw reduce { acc, throwable ->
    acc.addSuppressed(throwable)
    acc
  }
}
