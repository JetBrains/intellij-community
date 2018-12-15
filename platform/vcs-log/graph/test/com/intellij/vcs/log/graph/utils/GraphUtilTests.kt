// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.utils

import com.intellij.vcs.log.graph.TestGraphBuilder
import com.intellij.vcs.log.graph.asString
import com.intellij.vcs.log.graph.graph
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
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
}