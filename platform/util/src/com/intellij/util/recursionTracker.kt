// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

fun <C> findCycle(current: C, callers: (C) -> List<C>): List<C>? {
  for (path in traverseCallers(current, callers)) {
    if (path.last() === current) {
      return path.asReversed()
    }
  }
  return null
}

private fun <C> traverseCallers(current: C, callers: (C) -> List<C>): Sequence<List<C>> {
  return sequence {
    for (caller in callers(current)) {
      traverseCallersInner(emptyList(), caller, callers)
    }
  }
}

private suspend fun <C> SequenceScope<List<C>>.traverseCallersInner(
  pathBeforeCurrent: List<C>,
  current: C,
  callers: (C) -> List<C>,
) {
  val pathToCurrent = if (pathBeforeCurrent.isEmpty()) listOf(current) else pathBeforeCurrent + current
  yield(pathToCurrent)
  for (caller in callers(current)) {
    traverseCallersInner(pathToCurrent, caller, callers)
  }
}
