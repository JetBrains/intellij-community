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

class BfsWalk(val start: Int, private val graph: LiteLinearGraph, private val visited: Flags) {
  private val queue = ContainerUtil.newLinkedList(start)

  fun isFinished() = queue.isEmpty()

  fun currentNodes(): List<Int> = queue

  fun step(): List<Int> {
    while (!queue.isEmpty()) {
      val node = queue.poll()
      if (!visited.get(node)) {
        visited.set(node, true)
        val next = graph.getNodes(node, LiteLinearGraph.NodeFilter.DOWN).sorted()
        queue.addAll(next)
        return next
      }
    }
    return emptyList()
  }

  fun walk() {
    while (!isFinished()) {
      step()
    }
  }
}