// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.graph.utils.Flags
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

@ApiStatus.Internal
class ReachableNodes(private val graph: LiteLinearGraph) {
  private val visited: Flags = BitSetFlags(graph.nodesCount())

  fun getContainingBranches(nodeIndex: Int, branchNodeIndexes: Collection<Int>): Set<Int> {
    val result = HashSet<Int>()

    walk(listOf(nodeIndex), false) { node: Int ->
      if (branchNodeIndexes.contains(node)) result.add(node)
      true
    }

    return result
  }

  fun walkDown(headIds: Collection<Int>, consumer: Consumer<Int>) {
    walk(headIds, true) { node: Int ->
      consumer.accept(node)
      true
    }
  }

  fun walk(startNodes: Collection<Int>, goDown: Boolean, consumer: (Int) -> Boolean) {
    synchronized(visited) {
      visited.setAll(false)
      DfsWalk(startNodes, graph, visited).walk(goDown, consumer)
    }
  }
}
