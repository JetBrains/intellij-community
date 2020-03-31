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

import com.intellij.util.containers.IntStack
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags

object Dfs {
  interface NodeVisitor {
    fun enterNode(node: Int, previousNode: Int, travelDirection: Boolean)
    fun exitNode(node: Int)
  }

  object NextNode {
    const val NODE_NOT_FOUND = -1
    const val EXIT = -10
  }
}

private fun walk(start: Int, stack: IntStack, nextNodeFun: (Int) -> Int) {
  stack.push(start)

  while (!stack.empty()) {
    val nextNode = nextNodeFun(stack.peek())
    if (nextNode == Dfs.NextNode.EXIT) return
    if (nextNode != Dfs.NextNode.NODE_NOT_FOUND) {
      stack.push(nextNode)
    }
    else {
      stack.pop()
    }
  }
  stack.clear()
}

fun walk(start: Int, nextNodeFun: (Int) -> Int) {
  walk(start, IntStack(), nextNodeFun)
}

class DfsWalk(private val startNodes: Collection<Int>, private val graph: LiteLinearGraph, private val visited: Flags) {
  private val stack = IntStack()

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

/*
 * Depth-first walk for a graph. For each node, walks both into upward and downward siblings.
 * Tries to preserve direction of travel: when a node is entered from up-sibling, goes to the down-siblings first.
 * Then goes to the other up-siblings.
 * And when a node is entered from down-sibling, goes to the up-siblings first.
 * Then goes to the other down-siblings.
 * When a node is entered the first time, enterNode is called.
 * When a all the siblings of the node are visited, exitNode is called.
 */
fun LiteLinearGraph.walk(start: Int, visitor: Dfs.NodeVisitor) {
  if (start < 0 || start >= nodesCount()) return

  val visited = BitSetFlags(nodesCount(), false)

  val stack = IntStack()
  stack.push(start) // commit + direction of travel

  outer@ while (!stack.empty()) {
    val currentNode = stack.peek()
    val down = isDown(stack)
    if (!visited.get(currentNode)) {
      visited.set(currentNode, true)
      visitor.enterNode(currentNode, getPreviousNode(stack), down)
    }

    for (nextNode in getNodes(currentNode, if (down) LiteLinearGraph.NodeFilter.DOWN else LiteLinearGraph.NodeFilter.UP)) {
      if (!visited.get(nextNode)) {
        stack.push(nextNode)
        continue@outer
      }
    }

    for (nextNode in getNodes(currentNode, if (down) LiteLinearGraph.NodeFilter.UP else LiteLinearGraph.NodeFilter.DOWN)) {
      if (!visited.get(nextNode)) {
        stack.push(nextNode)
        continue@outer
      }
    }

    visitor.exitNode(currentNode)
    stack.pop()
  }
}

private fun getPreviousNode(stack: IntStack): Int {
  return if (stack.size() < 2) {
    Dfs.NextNode.NODE_NOT_FOUND
  }
  else stack.get(stack.size() - 2)
}

private fun isDown(stack: IntStack): Boolean {
  val currentNode = stack.peek()
  val previousNode = getPreviousNode(stack)
  if (previousNode == Dfs.NextNode.NODE_NOT_FOUND) return true
  return previousNode < currentNode
}