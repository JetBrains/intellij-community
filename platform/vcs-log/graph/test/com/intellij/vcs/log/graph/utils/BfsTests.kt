// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.utils

import com.intellij.vcs.log.graph.graph
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import org.junit.Test
import kotlin.test.assertEquals

class BfsTests {

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