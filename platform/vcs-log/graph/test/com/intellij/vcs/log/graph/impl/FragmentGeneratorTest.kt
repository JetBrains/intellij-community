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


import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.collapsing.FragmentGenerator
import com.intellij.vcs.log.graph.graph
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import org.junit.Assert.assertEquals
import org.junit.Test

private val LinearGraph.lite: LiteLinearGraph get() = LinearGraphUtils.asLiteLinearGraph(this)

private fun LinearGraph.getMiddleNodes(upNode: Int, downNode: Int) = FragmentGenerator(lite) { false }.getMiddleNodes(upNode, downNode, false)

private infix fun Collection<Int>.assert(s: String) = assertEquals(s, sorted().joinToString(","))
private infix fun Int?.assert(i: Int?) = assertEquals(i, this)

private fun LinearGraph.redNodes(vararg redNode: Int = IntArray(0)): FragmentGenerator {
  val redNodes = redNode.toSet()

  return FragmentGenerator(lite) {
    getNodeId(it) in redNodes
  }
}

private val Int?.s: String get() = this?.toString() ?: "n"

private infix fun FragmentGenerator.GreenFragment.assert(s: String)
    = assertEquals(s, "${upRedNode.s}|${downRedNode.s}|${middleGreenNodes.sorted().joinToString(",")}")

/*
0
|
1
|
2
 */
val simple = graph {
  0(1, 2)
  1(2)
  2()
}

/*
0
| 1
|/
2
 */
val twoBranch = graph {
  0(2)
  1(2)
  2()
}

/*
0
|\
1 2
|\|\
3 4 5
 */
val downTree = graph {
  0(1, 2)
  1(3, 4)
  2(4, 5)
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
val upTree = graph {
  0(3)
  1(3, 4)
  2(4)
  3(5)
  4(5)
  5()
}

/*
0
|\
| 1
2 |\
| | 3
| 4 |
|/  5
6  /|
|\/ /
|7 /
\ /
 8
 */
val difficult = graph {
  0(1, 2)
  1(3, 4)
  2(6)
  3(5)
  4(6)
  5(7, 8)
  6(7, 8)
  7()
  8()
}

class FragmentGeneratorTest {

  class MiddleNodesTest {
    @Test fun simple() = simple.getMiddleNodes(0, 2) assert "0,1,2"
    @Test fun empty() = twoBranch.getMiddleNodes(0, 1) assert ""
    @Test fun withDownRedundantBranches() = downTree.getMiddleNodes(0, 4) assert "0,1,2,4"
    @Test fun withUpRedundantBranches() = upTree.getMiddleNodes(1, 5) assert "1,3,4,5"

    @Test fun difficult1() = difficult.getMiddleNodes(1, 7) assert "1,3,4,5,6,7"
    @Test fun difficult2() = difficult.getMiddleNodes(0, 5) assert "0,1,3,5"
    @Test fun difficult3() = difficult.getMiddleNodes(1, 8) assert "1,3,4,5,6,8"
  }

  class NearRedNode {
    @Test fun simple1() = simple.redNodes(0).getNearRedNode(2, 10, true) assert 0
    @Test fun simple2() = simple.redNodes(2).getNearRedNode(0, 10, false) assert 2
    @Test fun simple3() = simple.redNodes(2, 1).getNearRedNode(0, 10, false) assert 1
    @Test fun simple4() = simple.redNodes(0, 1).getNearRedNode(0, 10, false) assert 0
    @Test fun simple5() = simple.redNodes(0, 1).getNearRedNode(2, 10, true) assert 1

    @Test fun downTree1() = downTree.redNodes(4, 5).getNearRedNode(0, 10, false) assert 4
    @Test fun downTree2() = downTree.redNodes(2, 3).getNearRedNode(0, 10, false) assert 2
    @Test fun upTree1() = upTree.redNodes(1, 2).getNearRedNode(5, 10, true) assert 2
    @Test fun upTree2() = upTree.redNodes(4, 0).getNearRedNode(5, 10, true) assert 4

    @Test fun difficult1() = difficult.redNodes(4, 5, 6).getNearRedNode(0, 10, false) assert 4
    @Test fun difficult2() = difficult.redNodes(2, 5, 6).getNearRedNode(1, 10, false) assert 5

    @Test fun nullAnswer1() = difficult.redNodes(8).getNearRedNode(0, 6, false) assert null
    @Test fun nullAnswer2() = difficult.redNodes(8).getNearRedNode(0, 7, false) assert 8
  }

  class GreenFragment {
    @Test fun simple1() = simple.redNodes(0).getGreenFragmentForCollapse(2, 10) assert "0|n|1,2"
    @Test fun simple2() = simple.redNodes(0, 2).getGreenFragmentForCollapse(1, 10) assert "0|2|1"
    @Test fun simple3() = simple.redNodes(0, 2).getGreenFragmentForCollapse(0, 10) assert "n|n|"

    @Test fun downTree1() = downTree.redNodes(4).getGreenFragmentForCollapse(2, 10) assert "n|4|0,2"
    @Test fun downTree2() = downTree.redNodes(4).getGreenFragmentForCollapse(1, 10) assert "n|4|0,1"

    @Test fun difficult1() = difficult.redNodes(1, 7).getGreenFragmentForCollapse(3, 10) assert "1|7|3,5"
    @Test fun difficult2() = difficult.redNodes(1, 7).getGreenFragmentForCollapse(8, 10) assert "1|n|3,4,5,6,8"
    @Test fun difficult3() = difficult.redNodes(1, 7).getGreenFragmentForCollapse(2, 10) assert "n|7|0,2,6"
  }
}