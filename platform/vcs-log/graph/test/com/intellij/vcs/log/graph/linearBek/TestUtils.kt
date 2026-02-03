// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.linearBek

import com.intellij.vcs.log.graph.TestGraphBuilder
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.asTestGraphString
import com.intellij.vcs.log.graph.graph
import com.intellij.vcs.log.graph.impl.facade.sort.SortChecker
import com.intellij.vcs.log.graph.impl.facade.sort.SortedBaseController
import com.intellij.vcs.log.graph.impl.facade.sort.bek.BekSorter
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutBuilder
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutImpl
import com.intellij.vcs.log.graph.utils.TimestampGetter
import org.junit.Assert.assertEquals
import kotlin.test.assertNull

class DummyTimestampGetter(val nodesCount: Int) : TimestampGetter {
  override fun getTimestamp(index: Int): Long {
    return 0
  }
}

fun buildLayout(graphBuilder: TestGraphBuilder.() -> Unit): GraphLayoutImpl {
  return GraphLayoutBuilder.build(graph(graphBuilder)) { nodeIndex1, nodeIndex2 -> nodeIndex1 - nodeIndex2 }
}

fun runBek(graphBuilder: TestGraphBuilder.() -> Unit): SortedBaseController.SortedLinearGraph {
  val beforeBek = graph(graphBuilder)
  val beforeLayout = buildLayout(graphBuilder)

  val bekMap = BekSorter.createBekMap(beforeBek, beforeLayout, DummyTimestampGetter(beforeBek.nodesCount()))
  val afterBek = SortedBaseController.SortedLinearGraph(bekMap, beforeBek)

  val edge = SortChecker.findReversedEdge(afterBek)
  if (edge != null) {
    assertNull(Pair(bekMap.getUsualIndex(edge.first), bekMap.getUsualIndex(edge.second)), "Found reversed edge")
  }

  return afterBek
}

fun runLinearBek(graphBuilder: TestGraphBuilder.() -> Unit): LinearBekGraph {
  val beforeLinearBek = graph(graphBuilder)
  val beforeLinearBekLayout = buildLayout(graphBuilder)

  val afterLinearBek = LinearBekGraph(beforeLinearBek)
  LinearBekGraphBuilder(afterLinearBek, beforeLinearBekLayout).collapseAll()
  return afterLinearBek
}

fun assertEquals(expected: TestGraphBuilder.() -> Unit, actual: LinearGraph) {
  assertEquals(graph(expected).asTestGraphString(), actual.asTestGraphString())
}
