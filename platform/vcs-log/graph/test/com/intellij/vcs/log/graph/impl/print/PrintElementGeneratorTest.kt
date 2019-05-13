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
package com.intellij.vcs.log.graph.impl.print

import com.intellij.util.NotNullFunction
import com.intellij.vcs.log.graph.AbstractTestWithTwoTextFile
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.elements.GraphNode
import com.intellij.vcs.log.graph.api.printer.PrintElementManager
import com.intellij.vcs.log.graph.asString
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutBuilder
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement
import com.intellij.vcs.log.graph.parser.LinearGraphParser
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

open class PrintElementGeneratorTest : AbstractTestWithTwoTextFile("elementGenerator") {

  class TestPrintElementManager(private val myGraphElementComparator: Comparator<GraphElement>) : PrintElementManager {

    override fun isSelected(printElement: PrintElementWithGraphElement): Boolean {
      return false
    }

    override fun getColorId(element: GraphElement): Int {
      if (element is GraphNode) {
        return element.nodeIndex
      }

      if (element is GraphEdge) {
        val normalEdge = LinearGraphUtils.asNormalEdge(element)
        if (normalEdge != null) return normalEdge.up + normalEdge.down
        return LinearGraphUtils.getNotNullNodeIndex(element)
      }

      throw IllegalStateException("Incorrect graph element type: " + element)
    }

    override fun getGraphElementComparator(): Comparator<GraphElement> {
      return myGraphElementComparator
    }
  }

  override fun runTest(`in`: String, out: String) {
    runTest(`in`, out, 7, 2, 10)
  }

  private fun runTest(`in`: String, out: String, longEdgeSize: Int, visiblePartSize: Int, edgeWithArrowSize: Int) {
    val graph = LinearGraphParser.parse(`in`)
    val graphLayout = GraphLayoutBuilder.build(graph) { o1, o2 -> o1.compareTo(o2) }
    val graphElementComparator = GraphElementComparatorByLayoutIndex(
      NotNullFunction<Int, Int> { nodeIndex -> graphLayout.getLayoutIndex(nodeIndex!!) }
    )
    val elementManager = TestPrintElementManager(graphElementComparator)
    val printElementGenerator = PrintElementGeneratorImpl(graph, elementManager, longEdgeSize, visiblePartSize, edgeWithArrowSize)
    val actual = printElementGenerator.asString(graph.nodesCount())
    assertEquals(out, actual)
  }

  @Test
  fun oneNode() {
    doTest("oneNode")
  }

  @Test
  fun manyNodes() {
    doTest("manyNodes")
  }

  @Test
  fun longEdges() {
    doTest("longEdges")
  }

  @Test
  fun specialElements() {
    doTest("specialElements")
  }

  //  oneUpOneDown tests were created in order to investigate some arrow behavior in upsource
  @Test
  fun oneUpOneDown1() {
    val testName = "oneUpOneDown1"
    runTest(loadText(testName + AbstractTestWithTwoTextFile.IN_POSTFIX), loadText(testName + AbstractTestWithTwoTextFile.OUT_POSTFIX), 7, 1,
            10)
  }

  @Test
  fun oneUpOneDown2() {
    val testName = "oneUpOneDown2"
    runTest(loadText(testName + AbstractTestWithTwoTextFile.IN_POSTFIX), loadText(testName + AbstractTestWithTwoTextFile.OUT_POSTFIX), 10,
            1, 10)
  }
}
