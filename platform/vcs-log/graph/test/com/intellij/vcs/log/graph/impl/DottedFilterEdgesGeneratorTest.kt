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
package com.intellij.vcs.log.graph.impl

import com.intellij.vcs.log.graph.TestGraphBuilder
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphNodeType
import com.intellij.vcs.log.graph.asTestGraphString
import com.intellij.vcs.log.graph.collapsing.CollapsedGraph
import com.intellij.vcs.log.graph.collapsing.DottedFilterEdgesGenerator
import com.intellij.vcs.log.graph.graph
import com.intellij.vcs.log.graph.utils.UnsignedBitSet
import org.junit.Assert.assertEquals
import org.junit.Test

class DottedFilterEdgesGeneratorTest {

  fun LinearGraph.assert(upIndex: Int = 0, downIndex: Int = nodesCount() - 1, result: TestGraphBuilder.() -> Unit) {
    val nodesVisibility = UnsignedBitSet()
    for (nodeIndex in 0 until nodesCount()) {
      val graphNode = getGraphNode(nodeIndex)
      nodesVisibility.set(getNodeId(nodeIndex), graphNode.type == GraphNodeType.USUAL)
    }
    val collapsedGraph = CollapsedGraph.newInstance(this, nodesVisibility)
    DottedFilterEdgesGenerator.update(collapsedGraph, upIndex, downIndex)

    val expectedResultGraph = graph(result)
    val actualResultGraph = collapsedGraph.compiledGraph

    assertEquals(expectedResultGraph.asTestGraphString(true), actualResultGraph.asTestGraphString(true))
  }

  @Test
  fun simple() = graph {
    1(2)
    2.UNM(3)
    3()
  }.assert {
    1(3.dot)
    3()
  }

  @Test
  fun simple2Up() = graph {
    1(3)
    2(3)
    3.UNM(4)
    4()
  }.assert {
    1(4.dot)
    2(4.dot)
    4()
  }

  @Test
  fun simple2Down() = graph {
    1(2)
    2.UNM(3, 4)
    3()
    4()
  }.assert {
    1(3.dot, 4.dot)
    3()
    4()
  }

  /*
  0
  |\
  1 2
  |\|\
  3 4 5
 */
  @Test
  fun downTree() = graph {
    0(1, 2)
    1.UNM(3, 4)
    2.UNM(4, 5)
    3()
    4()
    5()
  }.assert {
    0(3.dot, 4.dot, 5.dot)
    3()
    4()
    5()
  }

  /*
  0 1 2
  \/\/
  3 4
  \/
  5
 */
  @Test
  fun upTree() = graph {
    0(3)
    1(3, 4)
    2(4)
    3.UNM(5)
    4.UNM(5)
    5()
  }.assert {
    0(5.dot)
    1(5.dot)
    2(5.dot)
    5()
  }

  /*
  1
  |\
  2 |
  | 3
  4 |
  | 5
  |/
  6
 */
  @Test
  fun simpleMerge() = graph {
    1(2, 3)
    2(4)
    3(5)
    4.UNM(6)
    5.UNM(6)
    6()
  }.assert {
    1(2, 3)
    2(6.dot)
    3(6.dot)
    6()
  }

  @Test
  fun simpleMerge2() = graph {
    1(2, 3)
    2.UNM(4)
    3.UNM(5)
    4(6)
    5(6)
    6()
  }.assert {
    1(4.dot, 5.dot)
    4(6)
    5(6)
    6()
  }

  @Test
  fun fork() = graph {
    0(2)
    1(3)
    2(3)
    3.UNM(4)
    4.UNM(5)
    5()
  }.assert {
    0(2)
    1(5.dot)
    2(5.dot)
    5()
  }

}