// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.VcsLogGraphNodeId
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.graph.utils.Flags
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

@ApiStatus.Internal
class ReachableNodes(private val graph: LiteLinearGraph) {
  private val visited: Flags = BitSetFlags(graph.nodesCount())

  fun getContainingBranches(nodeId: VcsLogGraphNodeId, branchNodeIndexes: Collection<VcsLogGraphNodeId>): Set<VcsLogGraphNodeId> {
    val result = HashSet<VcsLogGraphNodeId>()

    walk(listOf(nodeId), false) { node: VcsLogGraphNodeId ->
      if (branchNodeIndexes.contains(node)) result.add(node)
      true
    }

    return result
  }

  fun walkDown(headIds: Collection<VcsLogGraphNodeId>, consumer: Consumer<VcsLogGraphNodeId>) {
    walk(headIds, true) { node: Int ->
      consumer.accept(node)
      true
    }
  }

  fun walk(startNodes: Collection<VcsLogGraphNodeId>, goDown: Boolean, consumer: (VcsLogGraphNodeId) -> Boolean) {
    synchronized(visited) {
      visited.setAll(false)
      DfsWalk(startNodes, graph, visited).walk(goDown, consumer)
    }
  }
}
