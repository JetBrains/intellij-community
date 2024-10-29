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
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import org.jetbrains.annotations.ApiStatus
import java.util.*

open class BfsWalk(val start: Int, private val graph: LiteLinearGraph, private val visited: Flags, private val down: Boolean = true) {
  constructor(start: Int, graph: LiteLinearGraph) : this(start, graph, BitSetFlags(graph.nodesCount()))

  protected open val queue: Queue<Int> = ContainerUtil.newLinkedList(start)

  open fun isFinished() = queue.isEmpty()

  fun currentNodes(): Queue<Int> = queue

  fun step(consumer: (Int) -> Boolean = { true }): List<Int> {
    while (!queue.isEmpty()) {
      val node = queue.poll()
      if (!visited.get(node)) {
        visited.set(node, true)
        if (!consumer(node)) return emptyList()
        val next = if (down) {
          graph.getNodes(node, LiteLinearGraph.NodeFilter.DOWN).sorted()
        }
        else {
          graph.getNodes(node, LiteLinearGraph.NodeFilter.UP).sortedDescending()
        }
        queue.addAll(next)
        return next
      }
    }
    return emptyList()
  }

  fun walk(consumer: (Int) -> Boolean = { true }) {
    while (!isFinished()) {
      step(consumer)
    }
  }
}

@ApiStatus.Internal
open class BfsSearch<T>(start: Int, graph: LiteLinearGraph, visited: Flags, down: Boolean = true,
                        private val limit: Int = graph.nodesCount()) : BfsWalk(start, graph, visited, down) {
  var result: T? = null
    private set
  var count = 0

  override fun isFinished(): Boolean = result != null || queue.isEmpty() || count > limit

  fun find(consumer: (Int) -> T?): T? {
    count = 0
    walk { node ->
      result = consumer(node) ?: return@walk true
      count++
      return@walk false
    }
    return result
  }
}