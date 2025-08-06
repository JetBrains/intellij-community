// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.api

internal class Graph<T>(
  val nodes: Iterable<T>,
  val deps: (T) -> List<T>,
) {

  fun sortedTopologically(): List<T> {
    val visited = mutableSetOf<T>()
    val result = mutableListOf<T>()

    fun dfs(item: T) {
      if (!visited.add(item)) {
        return
      }
      deps(item).forEach(::dfs)
      result += item
    }

    nodes.forEach(::dfs)
    return result
  }

  fun findCycle(): List<T>? {
    val visiting = mutableSetOf<T>()
    val visited = mutableSetOf<T>()
    val pathStack = ArrayDeque<T>()

    fun dfs(node: T): List<T>? {
      if (node in visiting) {
        // Found a cycle - reconstruct it from the stack
        val cycleStart = pathStack.indexOf(node)
        return pathStack.drop(cycleStart)
      }
      if (node in visited) return null

      visiting.add(node)
      pathStack.addLast(node)

      for (dependent in deps(node)) {
        val cycle = dfs(dependent)
        if (cycle != null) return cycle
      }

      pathStack.removeLast()
      visiting.remove(node)
      visited.add(node)
      return null
    }

    for (node in nodes) {
      if (node !in visited) {
        val cycle = dfs(node)
        if (cycle != null) return cycle
      }
    }

    return null
  }
}