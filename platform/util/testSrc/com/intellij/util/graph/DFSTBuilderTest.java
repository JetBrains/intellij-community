/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.graph;

import com.intellij.util.containers.HashMap;
import junit.framework.TestCase;

import java.util.*;

/**
 *  @author dsl
 */
public class DFSTBuilderTest extends TestCase {
  public void testGraph() {
    final TestNode nA = new TestNode("A");
    final TestNode nB = new TestNode("B");
    final TestNode nC = new TestNode("C");
    final TestNode nD = new TestNode("D");
    final TestNode nE = new TestNode("E");
    final TestNode nF = new TestNode("F");
    final TestNode[] allNodes = new TestNode[]{nA, nB, nC, nD, nE, nF};
    final Map<TestNode, TestNode[]> map = new HashMap<TestNode, TestNode[]>();
    map.put(nA, new TestNode[0]);
    map.put(nB, new TestNode[0]);
    map.put(nC, new TestNode[]{nA, nB});
    map.put(nD, new TestNode[]{nA, nC});
    map.put(nE, new TestNode[]{nC});
    map.put(nF, new TestNode[]{nB});
    GraphGenerator<TestNode> graph = graphByNodes(allNodes, map);
    final DFSTBuilder<TestNode> dfstBuilder = new DFSTBuilder<TestNode>(graph);
    if (!dfstBuilder.isAcyclic()) {
      fail("Acyclic graph expected");
      return;
    }
    final Comparator<TestNode> comparator = dfstBuilder.comparator();
    assertTrue(comparator.compare(nA, nC) < 0);
    assertTrue(comparator.compare(nB, nC) < 0);
    assertTrue(comparator.compare(nA, nD) < 0);
    assertTrue(comparator.compare(nC, nD) < 0);
    assertTrue(comparator.compare(nC, nE) < 0);
    assertTrue(comparator.compare(nB, nF) < 0);
  }

  private GraphGenerator<TestNode> graphByNodes(final TestNode[] allNodes,
                                                                final Map<TestNode, TestNode[]> map) {
    final GraphGenerator<TestNode> graph = new GraphGenerator<TestNode>(new GraphGenerator.SemiGraph<TestNode>() {
      @Override
      public Collection<TestNode> getNodes() {
        return Arrays.asList(allNodes);
      }

      @Override
      public Iterator<TestNode> getIn(TestNode n) {
        return GraphTestUtil.iteratorOfArray(map.get(n));
      }
    });
    return graph;
  }

  public void testCircularDependency() {
    final TestNode nA = new TestNode("A");
    final TestNode nB = new TestNode("B");
    final TestNode nC = new TestNode("C");
    final TestNode nD = new TestNode("D");
    final TestNode[] allNodes = new TestNode[]{nA, nB, nC, nD};
    final Map<TestNode, TestNode[]> map = new HashMap<TestNode, TestNode[]>();
    map.put(nA, new TestNode[0]);
    map.put(nB, new TestNode[0]);
    map.put(nC, new TestNode[]{nA, nB, nD});
    map.put(nD, new TestNode[]{nC, nB});
    checkCircularDependecyDetected(allNodes, map);
  }


  private void checkCircularDependecyDetected(final TestNode[] allNodes,
                                                              final Map<TestNode, TestNode[]> map) {
    GraphGenerator<TestNode> graph = graphByNodes(allNodes, map);
    final DFSTBuilder<TestNode> dfstBuilder = new DFSTBuilder<TestNode>(graph);
    assertTrue (dfstBuilder.getCircularDependency() != null);
  }

  public void testCircularDependency2() {
    final TestNode nA = new TestNode("A");
    final TestNode nB = new TestNode("B");
    final TestNode[] allNodes = new TestNode[]{nA, nB};
    final Map<TestNode, TestNode[]> map = new HashMap<TestNode, TestNode[]>();
    map.put(nA, new TestNode[]{nB});
    map.put(nB, new TestNode[]{nA});
    checkCircularDependecyDetected(allNodes, map);
  }

  public void testCircularDependency3() {
    final TestNode nA = new TestNode("A");
    final TestNode nB = new TestNode("B");
    final TestNode nC = new TestNode("C");
    final TestNode[] allNodes = new TestNode[]{nA, nB, nC};
    final Map<TestNode, TestNode[]> map = new HashMap<TestNode, TestNode[]>();
    map.put(nA, new TestNode[]{nB});
    map.put(nB, new TestNode[]{nA});
    map.put(nC, new TestNode[0]);
    checkCircularDependecyDetected(allNodes, map);
  }

  public void testTNumberingSimple () {
    final TestNode nA = new TestNode("A");
    final TestNode nB = new TestNode("B");
    final TestNode nC = new TestNode("C");
    final TestNode nD = new TestNode("D");
    final TestNode[] allNodes = new TestNode[]{nA, nB, nC, nD};
    final Map<TestNode, TestNode[]> map = new HashMap<TestNode, TestNode[]>();
    map.put(nA, new TestNode[]{nC});
    map.put(nB, new TestNode[]{nA});
    map.put(nC, new TestNode[]{nB});
    map.put(nD, new TestNode[]{nB});
    GraphGenerator<TestNode> graph = graphByNodes(allNodes, map);
    final DFSTBuilder<TestNode> dfstBuilder = new DFSTBuilder<TestNode>(graph);
    assertTrue (!dfstBuilder.isAcyclic());
    Comparator<TestNode> comparator = dfstBuilder.comparator();
    assertTrue(comparator.compare(nA, nD) < 0);
    assertTrue(comparator.compare(nB, nD) < 0);
    assertTrue(comparator.compare(nC, nD) < 0);
  }
}
