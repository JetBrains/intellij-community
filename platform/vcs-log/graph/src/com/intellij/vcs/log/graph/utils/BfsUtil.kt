/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import java.util.*

object BfsUtil {
  fun getCorrespondingParent(graph: LiteLinearGraph, startNode: Int, endNode: Int, visited: Flags): Int {
    val candidates = graph.getNodes(startNode, LiteLinearGraph.NodeFilter.DOWN)
    if (candidates.size == 1) return candidates[0]
    if (candidates.contains(endNode)) return endNode

    val queues = ArrayList<Queue<Int>>(candidates.size)
    for (candidate in candidates) {
      queues.add(ContainerUtil.newLinkedList(candidate))
    }

    var emptyCount: Int
    visited.setAll(false)
    do {
      emptyCount = 0
      for (queue in queues) {
        if (queue.isEmpty()) {
          emptyCount++
        }
        else {
          val found = runNextBfsStep(graph, queue, visited, endNode)
          if (found) {
            return candidates[queues.indexOf(queue)]
          }
        }
      }
    }
    while (emptyCount < queues.size)

    return candidates[0]
  }

  private fun runNextBfsStep(graph: LiteLinearGraph, queue: Queue<Int>, visited: Flags, target: Int): Boolean {
    while (!queue.isEmpty()) {
      val node = queue.poll()
      if (!visited.get(node!!)) {
        visited.set(node, true)
        val next = graph.getNodes(node, LiteLinearGraph.NodeFilter.DOWN)
        if (next.contains(target)) return true
        queue.addAll(next)
        return false
      }
    }
    return false
  }
}
