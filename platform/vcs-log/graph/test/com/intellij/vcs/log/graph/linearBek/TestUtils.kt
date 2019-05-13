/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.linearBek

import com.intellij.vcs.log.graph.TestGraphBuilder
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.asTestGraphString
import com.intellij.vcs.log.graph.graph
import com.intellij.vcs.log.graph.impl.facade.BekBaseController
import com.intellij.vcs.log.graph.impl.facade.bek.BekChecker
import com.intellij.vcs.log.graph.impl.facade.bek.BekSorter
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutBuilder
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutImpl
import com.intellij.vcs.log.graph.utils.TimestampGetter
import org.junit.Assert.assertEquals
import kotlin.test.assertNull

class DummyTimestampGetter(val nodesCount: Int) : TimestampGetter {
  override fun size(): Int {
    return nodesCount
  }

  override fun getTimestamp(index: Int): Long {
    return 0
  }
}

fun buildLayout(graphBuilder: TestGraphBuilder.() -> Unit): GraphLayoutImpl {
  return GraphLayoutBuilder.build(graph(graphBuilder), { nodeIndex1, nodeIndex2 -> nodeIndex1 - nodeIndex2 })
}

fun runBek(graphBuilder: TestGraphBuilder.() -> Unit): BekBaseController.BekLinearGraph {
  val beforeBek = graph(graphBuilder)
  val beforeLayout = buildLayout(graphBuilder)

  val bekMap = BekSorter.createBekMap(beforeBek, beforeLayout, DummyTimestampGetter(beforeBek.nodesCount()))
  val afterBek = BekBaseController.BekLinearGraph(bekMap, beforeBek)

  val edge = BekChecker.findReversedEdge(afterBek)
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
