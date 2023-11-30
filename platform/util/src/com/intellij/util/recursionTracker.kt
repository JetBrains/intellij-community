// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

fun <C> findCycle(current: C, callers: (C) -> List<C>): List<C>? {
  for (path in traverseCallers(current, callers)) {
    if (path.last() === current) {
      return path.asReversed()
    }
  }
  return null
}

private fun <C> traverseCallers(current: C, callers: (C) -> List<C>): Sequence<PersistentList<C>> = sequence {
  for (caller in callers(current)) {
    traverseCallersInner(persistentListOf(), caller, callers)
  }
}

private suspend fun <C> SequenceScope<PersistentList<C>>.traverseCallersInner(
  pathBeforeCurrent: PersistentList<C>,
  current: C,
  callers: (C) -> List<C>,
) {
  val pathToCurrent = pathBeforeCurrent.add(current)
  yield(pathToCurrent)
  for (caller in callers(current)) {
    traverseCallersInner(pathToCurrent, caller, callers)
  }
}
