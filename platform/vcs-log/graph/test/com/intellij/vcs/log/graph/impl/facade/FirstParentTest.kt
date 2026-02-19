// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.TestGraphBuilder
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.graph
import com.intellij.vcs.log.graph.linearBek.assertEquals
import org.junit.Test

class FirstParentTest {
  private fun LinearGraph.assertFirstParent(startNodes: Set<Int>, matchedNodes: Set<Int>? = null, expected: TestGraphBuilder.() -> Unit) {
    val actualGraph = FirstParentController.buildCollapsedGraph(this, startNodes, matchedNodes).compiledGraph
    assertEquals(expected, actualGraph)
  }

  @Test
  fun simpleMerge() {
    graph {
      0(1)
      1(2, 3)
      2(4)
      3(4)
      4()
    }.assertFirstParent(setOf(0)) {
      0(1)
      1(2)
      2(4)
      4()
    }
  }

  @Test
  fun twoBranches() {
    graph {
      0(2)
      1(5)
      2(3, 4)
      3(6)
      4(5)
      5(6)
      6()
    }.assertFirstParent(setOf(0, 1)) {
      0(2)
      1(5)
      2(3)
      3(6)
      5(6)
      6()
    }
  }

  @Test
  fun startNotAtHead() {
    graph {
      0(1)
      1(2, 3)
      2(4)
      3(4)
      4()
    }.assertFirstParent(setOf(3)) {
      3(4)
      4()
    }
  }

  @Test
  fun featureBranchMerges() {
    // main branch merged into feature, then feature merged into master
    graph {
      0(2)
      1(3)
      2(5, 3)
      3(4)
      4(6, 5)
      5(7)
      6(7)
      7()
    }.assertFirstParent(setOf(0, 1)) {
      0(2)
      1(3)
      2(5)
      3(4)
      4(6)
      5(7)
      6(7)
      7()
    }
  }

  @Test
  fun filteredMerge() {
    graph {
      0(1)
      1(2, 3)
      2(4)
      3(4)
      4()
    }.assertFirstParent(setOf(0), setOf(1, 3, 4)) {
      1(4.dot)
      4()
    }
  }
}