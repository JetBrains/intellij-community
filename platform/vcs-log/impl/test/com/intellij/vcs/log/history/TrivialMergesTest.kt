// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.vcs.log.graph.TestGraphBuilder
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.collapsing.DottedFilterEdgesGenerator
import com.intellij.vcs.log.graph.graph
import com.intellij.vcs.log.graph.impl.assert
import org.junit.Test

class TrivialMergesTest {

  fun LinearGraph.assert(vararg toHide: Int, result: TestGraphBuilder.() -> Unit) {
    assert({ collapsedGraph ->
             DottedFilterEdgesGenerator.update(collapsedGraph, 0, nodesCount() - 1)
             hideTrivialMerges(collapsedGraph) {
               toHide.contains(it)
             }
           }, result)
  }


  /*
   0.
   |\
   | 1
   2.|
   |/
   3.
   */
  @Test
  fun simpleTrivialDiamond() = graph {
    0(1, 2)
    1.UNM(3)
    2(3)
    3()
  }.assert(0) {
    2(3)
    3()
  }

  /*
   0.
   |\
   | 1.
   2.|
   |/
   3.
   */
  @Test
  fun simpleNonTrivialDiamond() = graph {
    0(1, 2)
    1(3)
    2(3)
    3()
  }.assert(0) {
    0(1, 2)
    1(3)
    2(3)
    3()
  }

  /*
     0.
    /|
   1.|
   | 2
   3 |
   | 4.
   |/|
   5.|
   | 6
   7 |
   | 8.
   |/|
   9.|
   |/
   10
   */
  @Test
  fun multipleTrivialMerges() = graph {
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

  /*
     0.
    /|
   1.|
   | 2.
   3 |
   | 4.
   |/|
   5.|
   | 6
   7 |
   | 8.
   |/|
   9.|
   |/
   10
   */
  @Test
  fun multipleMergesOneNonTrivial() = graph {
    0(1, 2)
    1(3)
    2(4)
    3.UNM(5)
    4(5, 6)
    5(7)
    6.UNM(8)
    7.UNM(9)
    8(9, 10)
    9(10)
    10.UNM()
  }.assert(0, 4, 8) {
    0(1, 2)
    1(5.dot)
    2(5.dot)
    5(9.dot)
    9()
  }

  /*
   0.
   |
   1.
   |\\
   2 |\
   | 3.\
   4.|\ \
   |/ / |
   5./  |
   |/  /
   6. /
   | 7.
   |/|
   8.|
   |/
   9.
   */
  @Test
  fun octopusMerge() = graph {
    0(1)
    1(2, 3, 7)
    2.UNM(4)
    3(5, 6)
    4(5)
    5(6)
    6(8)
    7(8, 9)
    8(9)
    9()
  }.assert(1, 3, 7) {
    0(4.dot)
    4(5)
    5(6)
    6(8)
    8(9)
    9()
  }

  /*
   0.
   |
   1.
   |\\
   2 |\
   | 3.\
   4.|\ \
   |/ / |
   5./  |
   |/  /
   6. /
   | 7.
   |/|
   8.|
   |/
   9.
   */
  @Test
  fun nonTrivialOctopusMerge() = graph {
    0(1)
    1(2, 3, 7)
    2.UNM(4)
    3(5, 6)
    4(5)
    5(6)
    6(8)
    7(8, 9)
    8(9)
    9()
  }.assert(1, 7) {
    0(1)
    1(4.dot, 3.u, 8.dot)
    3(5, 6)
    4(5)
    5(6)
    6(8)
    8(9)
    9()
  }
}