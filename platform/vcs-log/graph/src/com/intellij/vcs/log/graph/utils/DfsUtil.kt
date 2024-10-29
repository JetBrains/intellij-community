// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.utils

import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object Dfs {
  object NextNode {
    const val NODE_NOT_FOUND = -1
    const val EXIT = -10
  }
}

private fun walk(start: Int, stack: IntArrayList, nextNodeFun: (Int) -> Int) {
  stack.push(start)

  while (!stack.isEmpty) {
    val nextNode = nextNodeFun(stack.topInt())
    if (nextNode == Dfs.NextNode.EXIT) return
    if (nextNode != Dfs.NextNode.NODE_NOT_FOUND) {
      stack.push(nextNode)
    }
    else {
      stack.popInt()
    }
  }
  stack.clear()
}

@ApiStatus.Internal
fun walk(start: Int, nextNodeFun: (Int) -> Int) {
  walk(start, IntArrayList(), nextNodeFun)
}

class DfsWalk(private val startNodes: Collection<Int>, private val graph: LiteLinearGraph, private val visited: Flags) {
  private val stack = IntArrayList()

  constructor(startNodes: Collection<Int>, linearGraph: LinearGraph) :
    this(startNodes, LinearGraphUtils.asLiteLinearGraph(linearGraph), BitSetFlags(linearGraph.nodesCount()))

  fun walk(goDown: Boolean, consumer: (Int) -> Boolean) {
    for (start in startNodes) {
      if (start < 0) continue
      if (visited.get(start)) continue
      visited.set(start, true)
      if (!consumer(start)) return

      walk(start, stack) nextNode@{ currentNode ->
        for (downNode in graph.getNodes(currentNode, if (goDown) LiteLinearGraph.NodeFilter.DOWN else LiteLinearGraph.NodeFilter.UP)) {
          if (!visited.get(downNode)) {
            visited.set(downNode, true)
            if (!consumer(downNode)) return@nextNode Dfs.NextNode.EXIT
            return@nextNode downNode
          }
        }

        Dfs.NextNode.NODE_NOT_FOUND
      }
    }
  }
}