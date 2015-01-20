/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.impl.print;

import com.intellij.openapi.util.Pair;
import com.intellij.util.NotNullFunction;
import com.intellij.vcs.log.graph.AbstractTestWithTwoTextFile;
import com.intellij.vcs.log.graph.GraphPackage;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.printer.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.api.printer.PrintElementsManager;
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutBuilder;
import com.intellij.vcs.log.graph.parser.LinearGraphParser;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.util.Comparator;

import static org.junit.Assert.assertEquals;

public class PrintElementGeneratorTest extends AbstractTestWithTwoTextFile {

  public PrintElementGeneratorTest() {
    super("elementGenerator");
  }

  private static class TestPrintElementManager implements PrintElementsManager {
    @NotNull
    private final Comparator<GraphElement> myGraphElementComparator;

    private TestPrintElementManager(@NotNull Comparator<GraphElement> graphElementComparator) {
      myGraphElementComparator = graphElementComparator;
    }

    @Override
    public boolean elementIsSelected(@NotNull PrintElementWithGraphElement printElement) {
      return false;
    }

    @Override
    public int getColorId(@NotNull GraphElement element) {
      if (element instanceof GraphNode) {
        return ((GraphNode)element).getNodeIndex();
      }

      if (element instanceof GraphEdge) {
        GraphEdge edge = (GraphEdge)element;
        Pair<Integer, Integer> normalEdge = LinearGraphUtils.asNormalEdge(edge);
        if (normalEdge != null)
          return normalEdge.first + normalEdge.second;
        return LinearGraphUtils.getNotNullNodeIndex(edge);
      }

      throw new IllegalStateException("Incorrect graph element type: " + element);
    }

    @NotNull
    @Override
    public Comparator<GraphElement> getGraphElementComparator() {
      return myGraphElementComparator;
    }
  }

  @Override
  protected void runTest(String in, String out) {
    LinearGraph graph = LinearGraphParser.parse(in);
    final GraphLayout graphLayout = GraphLayoutBuilder.build(graph, new Comparator<Integer>() {
      @Override
      public int compare(@NotNull Integer o1, @NotNull Integer o2) {
        return o1.compareTo(o2);
      }
    });
    Comparator<GraphElement> graphElementComparator = new GraphElementComparatorByLayoutIndex(new NotNullFunction<Integer, Integer>() {
      @NotNull
      @Override
      public Integer fun(Integer nodeIndex) {
        return graphLayout.getLayoutIndex(nodeIndex);
      }
    });
    TestPrintElementManager elementManager = new TestPrintElementManager(graphElementComparator);
    PrintElementGeneratorImpl printElementGenerator =
      new PrintElementGeneratorImpl(graph, elementManager, 7, 2, 10);
    String actual = GraphPackage.asString(printElementGenerator, graph.nodesCount());
    assertEquals(out, actual);
  }

  @Test
  public void oneNode() throws IOException {
    doTest("oneNode");
  }

  @Test
  public void manyNodes() throws IOException {
    doTest("manyNodes");
  }

  @Test
  public void longEdges() throws IOException {
    doTest("longEdges");
  }

  @Test
  public void specialElements() throws IOException {
    doTest("specialElements");
  }

}
