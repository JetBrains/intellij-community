// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.impl

import com.intellij.vcs.log.graph.TestGraphBuilder
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.collapsing.DottedFilterEdgesGenerator
import com.intellij.vcs.log.graph.graph
import org.junit.Test

class InplaceFilterEdgesGeneratorTest {

  fun LinearGraph.assert(vararg toHide: Int, result: TestGraphBuilder.() -> Unit) {
    assert({ collapsedGraph ->
             DottedFilterEdgesGenerator.update(collapsedGraph, 0, nodesCount() - 1)
             DottedFilterEdgesGenerator.hideInplace(collapsedGraph,
                                                    toHide.map { collapsedGraph.compiledGraph.getNodeIndex(it)!! }.toSet())
           }, result)
  }

  /*
   1
   |
   2
   |
   3
   |
   4
   */
  @Test
  fun simpleLine() = graph {
    1(2)
    2.UNM(3)
    3(4)
    4()
  }.assert(3) {
    1(4.dot)
    4()
  }

  /*
   0
   |\
   | 1
   2 |
   |/
   3
  */
  @Test
  fun simpleTriangularMerge() = graph {
    0(1, 2)
    1(3)
    2.UNM(3)
    3()
  }.assert(0) {
    1(3)
    3()
  }

  /*
   1
   |
   2
   |\
   | 3
   5  \
   |   4
   6
   */
  @Test
  fun twoRootsMerge() = graph {
    1(2)
    2(3, 5)
    3.UNM(4)
    4()
    5.UNM(6)
    6()
  }.assert(2) {
    1(4.dot, 6.dot)
    4()
    6()
  }

  /*
     0
    /|
   1 |
   | 2
   3 |
   | 4
   |/|
   5 |
   | 6
   7 |
   | 8
   |/|
   9 |
   |/
   10
   */
  @Test
  fun multipleMerges() = graph {
    0(1, 2)
    1(3)
    2.UNM(4)
    3.UNM(5)
    4(5, 6)
    5(7)
    6.UNM(8)
    7.UNM(9)
    8(9, 10)
    9(10)
    10.UNM()
  }.assert(0, 4, 8) {
    1(5.dot)
    5(9.dot)
    9()
  }
}