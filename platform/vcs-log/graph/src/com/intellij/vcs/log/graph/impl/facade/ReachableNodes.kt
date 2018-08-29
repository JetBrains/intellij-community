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

package com.intellij.vcs.log.graph.impl.facade

import com.intellij.util.Consumer
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.utils.DfsUtil
import com.intellij.vcs.log.graph.utils.Flags
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.UnsignedBitSet
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import java.util.*

class ReachableNodes(private val graph: LiteLinearGraph) {
  private val flags: Flags = BitSetFlags(graph.nodesCount())

  fun getContainingBranches(nodeIndex: Int, branchNodeIndexes: Collection<Int>): Set<Int> {
    val result = HashSet<Int>()

    walk(listOf(nodeIndex), false, Consumer { node -> if (branchNodeIndexes.contains(node)) result.add(node) })

    return result
  }

  fun walk(headIds: Collection<Int>, consumer: Consumer<Int>) {
    walk(headIds, true, consumer)
  }

  private fun walk(startNodes: Collection<Int>, goDown: Boolean, consumer: Consumer<Int>) {
    walk(startNodes, goDown) { node: Int ->
      consumer.consume(node)
      true
    }
  }

  fun walk(startNodes: Collection<Int>, goDown: Boolean, consumer: (Int) -> Boolean) {
    synchronized(flags) {

      flags.setAll(false)
      for (start in startNodes) {
        if (start < 0) continue
        if (flags.get(start)) continue
        flags.set(start, true)
        if (!consumer(start)) return

        DfsUtil.walk(start) nextNode@{ currentNode ->
          for (downNode in graph.getNodes(currentNode, if (goDown) LiteLinearGraph.NodeFilter.DOWN else LiteLinearGraph.NodeFilter.UP)) {
            if (!flags.get(downNode)) {
              flags.set(downNode, true)
              if (!consumer(downNode)) return@nextNode DfsUtil.NextNode.EXIT
              return@nextNode downNode
            }
          }

          DfsUtil.NextNode.NODE_NOT_FOUND
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun getReachableNodes(permanentGraph: LinearGraph, headNodeIndexes: Set<Int>?): UnsignedBitSet {
      if (headNodeIndexes == null) {
        val nodesVisibility = UnsignedBitSet()
        nodesVisibility.set(0, permanentGraph.nodesCount() - 1, true)
        return nodesVisibility
      }

      val result = UnsignedBitSet()
      val getter = ReachableNodes(LinearGraphUtils.asLiteLinearGraph(permanentGraph))
      getter.walk(headNodeIndexes, Consumer { node -> result.set(node!!, true) })

      return result
    }
  }
}
