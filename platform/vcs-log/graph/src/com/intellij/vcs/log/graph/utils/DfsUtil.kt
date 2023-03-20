/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.vcs.log.graph.utils

import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import it.unimi.dsi.fastutil.ints.IntArrayList

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