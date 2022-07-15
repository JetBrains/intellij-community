// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import org.jetbrains.annotations.TestOnly

@TestOnly
fun runAll(vararg actions: () -> Unit) {
  actions.asSequence().runAll()
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
  runAllCatching().reduceAndThrow()
}

@TestOnly
fun runAllCatching(vararg actions: () -> Unit): List<Throwable> {
  return actions.asSequence().runAllCatching()
}

@TestOnly
fun runAllCatching(actions: Iterable<() -> Unit>): List<Throwable> {
  return actions.asSequence().runAllCatching()
}

@TestOnly
fun <X> runAllCatching(items: Collection<X>, action: (X) -> Unit): List<Throwable> {
  return items.asSequence().map {
    {
      action(it)
    }
  }.runAllCatching()
}

@TestOnly
fun Sequence<() -> Unit>.runAllCatching(): List<Throwable> {
  val result = SmartList<Throwable>()
  for (action in this) {
    try {
      action()
    }
    catch (e: CompoundRuntimeException) {
      result.addAll(e.exceptions)
    }
    catch (e: Throwable) {
      result.add(e)
    }
  }
  return if (result.isEmpty()) {
    emptyList()
  }
  else {
    result
  }
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
