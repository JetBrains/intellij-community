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

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.util.containers.IntStack
import com.intellij.util.containers.Stack
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
  val visited = BitSetFlags(nodesCount(), false)

  val stack = Stack<Pair<Int, Boolean>>()
  stack.push(Pair(start, true)) // commit + direction of travel

  outer@ while (!stack.empty()) {
    val currentNode = stack.peek().first
    val down = stack.peek().second
    if (!visited.get(currentNode)) {
      visited.set(currentNode, true)
      visitor.enterNode(currentNode, getPreviousNode(stack), down)
    }

    for (nextNode in getNodes(currentNode, if (down) LiteLinearGraph.NodeFilter.DOWN else LiteLinearGraph.NodeFilter.UP)) {
      if (!visited.get(nextNode)) {
        stack.push(Pair(nextNode, down))
        continue@outer
      }
    }

    for (nextNode in getNodes(currentNode, if (down) LiteLinearGraph.NodeFilter.UP else LiteLinearGraph.NodeFilter.DOWN)) {
      if (!visited.get(nextNode)) {
        stack.push(Pair(nextNode, !down))
        continue@outer
      }
    }

    visitor.exitNode(currentNode)
    stack.pop()
  }
}

private fun getPreviousNode(stack: Stack<Pair<Int, Boolean>>): Int {
  return if (stack.size < 2) {
    Dfs.NextNode.NODE_NOT_FOUND
  }
  else stack[stack.size - 2].first
}

fun LiteLinearGraph.isAncestor(lowerNode: Int, upperNode: Int): Boolean {
  val visited = BitSetFlags(nodesCount(), false)

  val result = Ref.create(false)
  walk(lowerNode) { currentNode ->
    visited.set(currentNode, true)

    if (currentNode == upperNode) {
      result.set(true)
      return@walk Dfs.NextNode.EXIT
    }
    if (currentNode > upperNode) {
      for (nextNode in getNodes(currentNode, LiteLinearGraph.NodeFilter.UP)) {
        if (!visited.get(nextNode)) {
          return@walk nextNode
        }
      }
    }

    Dfs.NextNode.NODE_NOT_FOUND
  }

  return result.get()
}

fun walk(startRowIndex: Int, nextNodeFun: (Int) -> Int) {
  val stack = IntStack()
  stack.push(startRowIndex)

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
