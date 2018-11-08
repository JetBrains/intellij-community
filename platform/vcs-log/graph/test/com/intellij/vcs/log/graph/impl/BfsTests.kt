// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.impl

import com.intellij.vcs.log.graph.TestGraphBuilder
import com.intellij.vcs.log.graph.asString
import com.intellij.vcs.log.graph.graph
import com.intellij.vcs.log.graph.utils.BfsUtil
import com.intellij.vcs.log.graph.utils.BfsWalk
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import org.junit.Test
import kotlin.test.assertEquals

class BfsTests {

  private fun assertCorrespondingParent(startNode: Int, endNode: Int, expectedParent: Int, graphBuilder: TestGraphBuilder.() -> Unit) {
    val graph = graph(graphBuilder)
    val actualParent = BfsUtil.getCorrespondingParent(LinearGraphUtils.asLiteLinearGraph(graph), startNode, endNode,
                                                      BitSetFlags(graph.nodesCount()))
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

  @Test
  fun `test visited`() {
    val graph = graph {
      0(3)
      1(3)
      2(4)
      3(4)
      4(5, 6)
      5(7)
      6(8)
      7()
      8()
    }
    val nodesCount = graph.nodesCount()
    val visited = BitSetFlags(nodesCount)

    BfsWalk(0, LinearGraphUtils.asLiteLinearGraph(graph), visited).walk()
    assertEquals(BitSetFlags(nodesCount).setAll(0, 3, 4, 5, 6, 7, 8), visited)
    visited.setAll(false)

    BfsWalk(3, LinearGraphUtils.asLiteLinearGraph(graph), visited).walk()
    assertEquals(BitSetFlags(nodesCount).setAll(3, 4, 5, 6, 7, 8), visited)
    visited.setAll(false)

    BfsWalk(5, LinearGraphUtils.asLiteLinearGraph(graph), visited).walk()
    assertEquals(BitSetFlags(nodesCount).setAll(5, 7), visited)
    visited.setAll(false)

    BfsWalk(8, LinearGraphUtils.asLiteLinearGraph(graph), visited).walk()
    assertEquals(BitSetFlags(nodesCount).setAll(8), visited)
    visited.setAll(false)
  }

  private fun BitSetFlags.setAll(vararg values: Int): BitSetFlags {
    values.forEach { this[it] = true }
    return this
  }
}