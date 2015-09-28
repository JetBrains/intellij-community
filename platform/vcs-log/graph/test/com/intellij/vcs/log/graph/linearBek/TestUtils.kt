/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutBuilder
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutImpl
import com.intellij.vcs.log.graph.utils.TimestampGetter
import org.junit.Assert.assertEquals

public class DummyTimestampGetter(val nodesCount: Int) : TimestampGetter {
  override fun size(): Int {
    return nodesCount
  }

  override fun getTimestamp(index: Int): Long {
    return 0
  }
}

public fun buildLayout(graphBuilder: TestGraphBuilder.() -> Unit): GraphLayoutImpl {
  return GraphLayoutBuilder.build(graph(graphBuilder), {nodeIndex1, nodeIndex2 -> nodeIndex1 - nodeIndex2 })
}

public fun runLinearBek(graphBuilder: TestGraphBuilder.() -> Unit): LinearBekGraph {
  val beforeLinearBek = graph(graphBuilder)
  val beforeLinearBekLayout = buildLayout(graphBuilder)

  val afterLinearBek = LinearBekGraph(beforeLinearBek)
  LinearBekGraphBuilder(afterLinearBek, beforeLinearBekLayout).collapseAll()
  return afterLinearBek
}

public fun assertEquals(expected: TestGraphBuilder.() -> Unit, actual: LinearGraph): Unit {
  assertEquals(graph(expected).asTestGraphString(), actual.asTestGraphString())
}