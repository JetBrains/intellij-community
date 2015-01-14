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
import com.intellij.vcs.log.graph.graph
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutBuilder
import com.intellij.vcs.log.graph.utils.TimestampGetter
import org.junit.Assert.assertEquals
import com.intellij.vcs.log.graph.asString
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType
import org.junit.Test

class LinearBekExpandTest {
  fun runTest(beforeLinearBekBuilder: TestGraphBuilder.() -> Unit, afterExpansionBuilder: TestGraphBuilder.() -> Unit, fromNodeToExpand: Int, toNodeToExpand: Int) {
    val beforeLinearBek = graph(beforeLinearBekBuilder)
    val beforeLinearBekLayout = GraphLayoutBuilder.build(beforeLinearBek, {(nodeIndex1, nodeIndex2) -> nodeIndex1 - nodeIndex2 })

    val afterLinearBekExpected = graph(afterExpansionBuilder)
    val afterLinearBek = LinearBekController.compileGraph(beforeLinearBek, beforeLinearBekLayout, object : TimestampGetter {
      override fun getTimestamp(index: Int): Long {
        return 0
      }

      override fun size(): Int {
        return beforeLinearBek.nodesCount();
      }
    })
    afterLinearBek.expandEdge(GraphEdge.createNormalEdge(fromNodeToExpand, toNodeToExpand, GraphEdgeType.DOTTED));
    assertEquals(afterLinearBekExpected.asString(), afterLinearBek.asString())
  }

  /*
    0              0
    |\             |
    | 1            1
    | |\           |
    | | 2          2
    | | |          :
    | 3 |   ->     3
    | |/           |
    | 4            4
    | |            :
    5 |            5
    |/             |
    6              6
     */
  Test fun recursiveExpansionTest() = runTest({
    0(5, 1)
    1(3, 2)
    2(4)
    3(4)
    4(6)
    5(6)
    6()
  }, {
    0(5, 1)
    1(2)
    2(3.dot)
    3(4)
    4(6)
    5(6)
    6()
  }, 4, 5)
}