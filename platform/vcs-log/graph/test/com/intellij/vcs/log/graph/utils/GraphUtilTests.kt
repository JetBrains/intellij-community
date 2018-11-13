// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.utils

import com.intellij.vcs.log.graph.TestGraphBuilder
import com.intellij.vcs.log.graph.asString
import com.intellij.vcs.log.graph.graph
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import gnu.trove.TIntHashSet
import org.junit.Test
import kotlin.test.assertEquals

class GraphUtilTests {
  private fun assertCorrespondingParent(startNode: Int, endNode: Int, expectedParent: Int, graphBuilder: TestGraphBuilder.() -> Unit) {
    val graph = graph(graphBuilder)
    val actualParent = LinearGraphUtils.asLiteLinearGraph(graph).getCorrespondingParent(startNode, endNode, BitSetFlags(graph.nodesCount()))
    assertEquals(expectedParent, actualParent,
                 "Incorrect parent found when walking from ${startNode} to ${endNode} in ${graph.asString(true)}")
  }

  @Test
  fun `one parent`() {
    assertCorrespondingParent(0, 5, 1) {
      0(1)
      1(2)
      2(3, 4)
      3(5)
      4(5)
      5()
    }
  }

  @Test
  fun `target parent`() {
    assertCorrespondingParent(0, 2, 2) {
      0(1, 2)
      1(3)
      2(3)
      3()
    }
  }

  @Test
  fun `merge commit`() {
    assertCorrespondingParent(0, 4, 2) {
      0(1, 2)
      1(3)
      2(3, 4)
      3()
      4()
    }
  }

  private fun assertSubgraphDifference(node1: Int, node2: Int, expectedDifference: TIntHashSet, graphBuilder: TestGraphBuilder.() -> Unit) {
    val graph = graph(graphBuilder)
    val actualDifference = graph.subgraphDifference(node1, node2)
    assertEquals(expectedDifference, actualDifference,
                 "Incorrect subgraph difference calculated between ${node1} to ${node2} in ${graph.asString(true)}")
  }

  /*
   0
   | 1
   2 |
   | 3
   4 |
   | 5
   6 |
   |/
   7
   8
   9
   */
  @Test
  fun `single branches`() {
    assertSubgraphDifference(0, 1, TIntHashSet(intArrayOf(0, 2, 4, 6))) {
      0(2)
      1(3)
      2(4)
      3(5)
      4(6)
      5(7)
      6(7)
      7(8)
      8(9)
      9()
    }
  }

  /*
   0
   |\
   | 1
   2 |\
   | | 3
   | 4 |
   5 |/
   | 6
   |/
   7
   8
   */
  @Test
  fun ancestor() {
    assertSubgraphDifference(0, 3, TIntHashSet(intArrayOf(0, 1, 2, 4, 5))) {
      0(1, 2)
      1(3, 4)
      2(5)
      3(6)
      4(6)
      5(7)
      6(7)
      7(8)
      8()
    }
  }

  /*
   0
   |\
   | 1
   2 |\
   | | 3
   | 4 |
   5 |/
   | 6
   |/
   7
   8
  */
  @Test
  fun descendant() {
    assertSubgraphDifference(3, 0, TIntHashSet()) {
      0(1, 2)
      1(3, 4)
      2(5)
      3(6)
      4(6)
      5(7)
      6(7)
      7(8)
      8()
    }
  }

  /*
   0
   |  1
   2  |
   |\ 3
   | \|\
   |  4 |
   |  | 5
   |  |/
   | /|
   |/ |
   6  |
   |  7
   8 /
   |/
   9
   */
  @Test
  fun `multiple bases`() {
    assertSubgraphDifference(0, 1, TIntHashSet(intArrayOf(0, 2))) {
      0(2)
      1(3)
      2(4, 6)
      3(4, 5)
      4(7)
      5(6)
      6(8)
      7(9)
      8(9)
      9()
    }
  }
}