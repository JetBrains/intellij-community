// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.permanent

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.utils.Dfs
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.walk
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntComparator
import it.unimi.dsi.fastutil.ints.IntList
import org.jetbrains.annotations.ApiStatus
import java.util.function.IntPredicate
import kotlin.time.measureTimedValue

@ApiStatus.Internal
object GraphLayoutBuilder {
  private val LOG = Logger.getInstance(GraphLayoutBuilder::class.java)

  @JvmStatic
  fun build(graph: LinearGraph, comparator: IntComparator): GraphLayoutImpl {
    return build(graph, emptySet(), comparator)
  }

  @JvmStatic
  fun build(graph: LinearGraph, branches: Set<Int>, comparator: IntComparator): GraphLayoutImpl {
    val (graphHeads, headsTime) = measureTimedValue { graph.getHeads() }
    LOG.trace { "Graph heads found in ${headsTime.inWholeMilliseconds} ms: ${graphHeads.size} graph heads" }
    val (sortedHeads, sortTime) = measureTimedValue { IntArrayList(branches + graphHeads).sortCatching(comparator) }
    LOG.trace {
      "Heads sorted in ${sortTime.inWholeMilliseconds} ms: " +
      "${sortedHeads.size} heads from ${branches.size} branches and ${graphHeads.size} graph heads"
    }

    val (layout, walkTime) = measureTimedValue { build(graph, sortedHeads) }
    LOG.trace { "Layout walked in ${walkTime.inWholeMilliseconds} ms" }
    return layout
  }

  /**
   * Performs sorting, while catching exceptions from the comparator.
   */
  private fun IntList.sortCatching(comparator: IntComparator): IntList {
    try {
      sort(comparator)
    }
    catch (pce: ProcessCanceledException) {
      throw pce
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    return this
  }

  internal fun LinearGraph.getHeads(): IntList {
    val heads = IntArrayList()
    for (i in 0 until nodesCount()) {
      if (!LinearGraphUtils.hasUpNodes(this, i)) {
        heads.add(i)
      }
    }
    return heads
  }

  private fun build(graph: LinearGraph, sortedHeads: IntList): GraphLayoutImpl {
    val layoutIndex = IntArray(graph.nodesCount())
    val importantHeads = IntArrayList()
    // reused between walk iterations to reduce the number of created predicates
    var childWithoutLayoutIndex: Int
    val processor = IntPredicate { child ->
      if (layoutIndex[child] != 0) {
        return@IntPredicate true
      }
      childWithoutLayoutIndex = child
      false
    }

    var currentLayoutIndex = 1
    for (i in sortedHeads.indices) {
      val head = sortedHeads.getInt(i)
      if (layoutIndex[head] != 0) continue

      importantHeads.add(head)

      walk(head) { currentNode: Int ->
        val firstVisit = layoutIndex[currentNode] == 0
        if (firstVisit) layoutIndex[currentNode] = currentLayoutIndex

        childWithoutLayoutIndex = Dfs.NextNode.NODE_NOT_FOUND
        LinearGraphUtils.processDownNodes(graph, currentNode, processor)

        if (childWithoutLayoutIndex == Dfs.NextNode.NODE_NOT_FOUND) {
          if (firstVisit) currentLayoutIndex++
          return@walk Dfs.NextNode.NODE_NOT_FOUND
        }
        return@walk childWithoutLayoutIndex
      }
    }

    return GraphLayoutImpl(layoutIndex, importantHeads)
  }
}