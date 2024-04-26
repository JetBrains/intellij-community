// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.permanent

import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.graph
import it.unimi.dsi.fastutil.ints.IntComparator
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphLayoutBuilderHeadOrderTest {
  @Test
  fun linearGraph() {
    val graph = graph {
      0(1)
      1(2)
      2(3)
      3()
    }
    graph.assertHeadsForCommits(branchHeads = setOf(0, 2), headComparator = order(0, 2), expectedHeads = listOf(0, 0, 0, 0))
    graph.assertHeadsForCommits(branchHeads = setOf(0, 2), headComparator = order(2, 0), expectedHeads = listOf(0, 0, 2, 2))
  }

  @Test
  fun branchingGraph() {
    val graph = graph {
      0(2)
      1(3)
      2(3)
      3(4)
      4()
    }
    graph.assertHeadsForCommits(branchHeads = setOf(0, 2), headComparator = order(0, 1, 2), expectedHeads = listOf(0, 1, 0, 0, 0))
    graph.assertHeadsForCommits(branchHeads = setOf(0, 2), headComparator = order(0, 2, 1), expectedHeads = listOf(0, 1, 0, 0, 0))
    graph.assertHeadsForCommits(branchHeads = setOf(0, 2), headComparator = order(1, 0, 2), expectedHeads = listOf(0, 1, 0, 1, 1))
    graph.assertHeadsForCommits(branchHeads = setOf(0, 2), headComparator = order(1, 2, 0), expectedHeads = listOf(0, 1, 2, 1, 1))
    graph.assertHeadsForCommits(branchHeads = setOf(0, 2), headComparator = order(2, 0, 1), expectedHeads = listOf(0, 1, 2, 2, 2))
    graph.assertHeadsForCommits(branchHeads = setOf(0, 2), headComparator = order(2, 1, 0), expectedHeads = listOf(0, 1, 2, 2, 2))
  }

  /**
   * Assert that [com.intellij.vcs.log.graph.api.GraphLayout] built for this graph has correct head commits for each commit in the graph.
   */
  private fun LinearGraph.assertHeadsForCommits(branchHeads: Set<Int>, headComparator: IntComparator, expectedHeads: List<Int>) {
    val graphLayout = GraphLayoutBuilder.build(this, branchHeads, headComparator)
    assertEquals(expectedHeads, getHeadNodeIndices(this, graphLayout))
  }

  /**
   * For each commit in the graph, computes corresponding head node.
   */
  private fun getHeadNodeIndices(graph: LinearGraph, graphLayout: GraphLayoutImpl): List<Int> {
    return List(graph.nodesCount()) { graphLayout.getOneOfHeadNodeIndex(it) }
  }

  private fun order(vararg list: Int): IntComparator {
    return IntComparator { i, j -> list.indexOf(i) - list.indexOf(j) }
  }
}