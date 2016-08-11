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

import com.intellij.util.ObjectUtils;
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
    final TestNode[] allNodes = {nA, nB, nC, nD, nE, nF};
    final Map<TestNode, TestNode[]> map = new HashMap<>();
    map.put(nA, new TestNode[0]);
    map.put(nB, new TestNode[0]);
    map.put(nC, new TestNode[]{nA, nB});
    map.put(nD, new TestNode[]{nA, nC});
    map.put(nE, new TestNode[]{nC});
    map.put(nF, new TestNode[]{nB});
    GraphGenerator<TestNode> graph = graphByNodes(allNodes, map);
    final DFSTBuilder<TestNode> dfstBuilder = new DFSTBuilder<>(graph);
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

  private static GraphGenerator<TestNode> graphByNodes(final TestNode[] allNodes, final Map<TestNode, TestNode[]> mapIn) {
    final GraphGenerator<TestNode> graph = new GraphGenerator<>(new GraphGenerator.SemiGraph<TestNode>() {
      @Override
      public Collection<TestNode> getNodes() {
        return Arrays.asList(allNodes);
      }

      @Override
      public Iterator<TestNode> getIn(TestNode n) {
        return GraphTestUtil.iteratorOfArray(ObjectUtils.notNull(mapIn.get(n), new TestNode[0]));
      }
    });
    return graph;
  }

  public void testCircularDependency() {
    final TestNode nA = new TestNode("A");
    final TestNode nB = new TestNode("B");
    final TestNode nC = new TestNode("C");
    final TestNode nD = new TestNode("D");
    final TestNode[] allNodes = {nA, nB, nC, nD};
    final Map<TestNode, TestNode[]> map = new HashMap<>();
    map.put(nA, new TestNode[0]);
    map.put(nB, new TestNode[0]);
    map.put(nC, new TestNode[]{nA, nB, nD});
    map.put(nD, new TestNode[]{nC, nB});
    checkCircularDependecyDetected(allNodes, map);
  }


  private static void checkCircularDependecyDetected(final TestNode[] allNodes,
                                                     final Map<TestNode, TestNode[]> map) {
    GraphGenerator<TestNode> graph = graphByNodes(allNodes, map);
    final DFSTBuilder<TestNode> dfstBuilder = new DFSTBuilder<>(graph);
    assertTrue (dfstBuilder.getCircularDependency() != null);
  }

  public void testCircularDependency2() {
    final TestNode nA = new TestNode("A");
    final TestNode nB = new TestNode("B");
    final TestNode[] allNodes = {nA, nB};
    final Map<TestNode, TestNode[]> map = new HashMap<>();
    map.put(nA, new TestNode[]{nB});
    map.put(nB, new TestNode[]{nA});
    checkCircularDependecyDetected(allNodes, map);
  }

  public void testCircularDependency3() {
    final TestNode nA = new TestNode("A");
    final TestNode nB = new TestNode("B");
    final TestNode nC = new TestNode("C");
    final TestNode[] allNodes = {nA, nB, nC};
    final Map<TestNode, TestNode[]> map = new HashMap<>();
    map.put(nA, new TestNode[]{nB});
    map.put(nB, new TestNode[]{nA});
    map.put(nC, new TestNode[0]);
    checkCircularDependecyDetected(allNodes, map);
  }

  public void testTNumberingSimple() {
    final TestNode nA = new TestNode("A");
    final TestNode nB = new TestNode("B");
    final TestNode nC = new TestNode("C");
    final TestNode nD = new TestNode("D");
    final TestNode[] allNodes = {nA, nB, nC, nD};
    final Map<TestNode, TestNode[]> map = new HashMap<>();
    map.put(nA, new TestNode[]{nC});
    map.put(nB, new TestNode[]{nA});
    map.put(nC, new TestNode[]{nB});
    map.put(nD, new TestNode[]{nB});
    GraphGenerator<TestNode> graph = graphByNodes(allNodes, map);
    final DFSTBuilder<TestNode> dfstBuilder = new DFSTBuilder<>(graph);
    assertFalse(dfstBuilder.isAcyclic());
    Comparator<TestNode> comparator = dfstBuilder.comparator();
    assertTrue(comparator.compare(nA, nD) < 0);
    assertTrue(comparator.compare(nB, nD) < 0);
    assertTrue(comparator.compare(nC, nD) < 0);
  }

  public void testStackOverflow() {
    final TestNode[] allNodes = new TestNode[10000];
    final Map<TestNode, TestNode[]> map = new HashMap<>();
    for (int i = 0; i < allNodes.length; i++) {
      allNodes[i] = new TestNode(i + "");
      if (i != 0) {
        map.put(allNodes[i], new TestNode[]{allNodes[i - 1]});
      }
    }
    map.put(allNodes[0], new TestNode[]{allNodes[allNodes.length - 1]});
    GraphGenerator<TestNode> graph = graphByNodes(allNodes, map);
    final DFSTBuilder<TestNode> dfstBuilder = new DFSTBuilder<>(graph);
    assertFalse(dfstBuilder.isAcyclic());
  }

  public void testSccsReportedInLoadingOrder() {
    final TestNode main = new TestNode("main");
    final TestNode dep = new TestNode("dep");
    final TestNode d = new TestNode("d");
    final TestNode d2 = new TestNode("d2");
    final TestNode resMain = new TestNode("resMain");
    final TestNode resDep = new TestNode("resDep");
    final TestNode[] allNodes = {main, dep, d, d2, resMain, resDep};
    final Map<TestNode, TestNode[]> mapIn = new HashMap<>();
    mapIn.put(main, new TestNode[]{d, resMain});
    mapIn.put(dep, new TestNode[]{main,resDep});
    mapIn.put(d, new TestNode[]{d2});
    mapIn.put(d2, new TestNode[]{dep, d});
    GraphGenerator<TestNode> graph = graphByNodes(allNodes, mapIn);

    final DFSTBuilder<TestNode> dfstBuilder = new DFSTBuilder<>(graph);
    assertTrue (!dfstBuilder.isAcyclic());
    Comparator<TestNode> comparator = dfstBuilder.comparator();
    assertTrue(comparator.compare(resMain, main) < 0);
    assertTrue(comparator.compare(resMain, d) < 0);
    assertTrue(comparator.compare(resMain, d2) < 0);
    assertTrue(comparator.compare(resDep, dep) < 0);
    assertTrue(comparator.compare(resMain, resDep) > 0); //reversed loading order
  }

  public void testReportedInLoadingOrder() {
    final TestNode o = new TestNode("o");
    final TestNode a = new TestNode("a");
    final TestNode b = new TestNode("b");
    final TestNode c = new TestNode("c");
    for (int oIndex = 0; oIndex<4; oIndex++) {
      List<TestNode> list = new ArrayList<>(Arrays.asList(a, b, c));
      list.add(oIndex, o);
      TestNode[] allNodes = list.toArray(new TestNode[list.size()]);

      Map<TestNode, TestNode[]> mapIn = new HashMap<>();
      mapIn.put(o, new TestNode[]{a,b,c});

      final DFSTBuilder<TestNode> dfstBuilder = new DFSTBuilder<>(graphByNodes(allNodes, mapIn));
      assertTrue (dfstBuilder.isAcyclic());
      Comparator<TestNode> comparator = dfstBuilder.comparator();
      TestNode[] sorted = allNodes.clone();
      Arrays.sort(sorted, comparator);
      assertEquals("All nodes: "+list, Arrays.asList(c,b,a,o), Arrays.asList(sorted));
    }
  }
  public void testSccReportedInLoadingOrder() {
    final TestNode o1 = new TestNode("o1");
    final TestNode o2 = new TestNode("o2");
    final TestNode a = new TestNode("a");
    final TestNode b = new TestNode("b");
    final TestNode c = new TestNode("c");
    for (int oIndex = 0; oIndex<4; oIndex++) {
      List<TestNode> list = new ArrayList<>(Arrays.asList(a, b, c));
      list.add(oIndex, o1);
      list.add(oIndex, o2);
      TestNode[] allNodes = list.toArray(new TestNode[list.size()]);

      Map<TestNode, TestNode[]> mapIn = new HashMap<>();
      mapIn.put(o1, new TestNode[]{a,b,c,o2});
      mapIn.put(o2, new TestNode[]{o1});

      final DFSTBuilder<TestNode> dfstBuilder = new DFSTBuilder<>(graphByNodes(allNodes, mapIn));
      assertFalse(dfstBuilder.isAcyclic());
      Comparator<TestNode> comparator = dfstBuilder.comparator();
      assertTrue("All nodes: "+list,comparator.compare(b, a) < 0); //reversed loading order
      assertTrue("All nodes: "+list,comparator.compare(c, a) < 0); //reversed loading order
      assertTrue("All nodes: "+list,comparator.compare(c, b) < 0); //reversed loading order
      assertTrue("All nodes: "+list,comparator.compare(a, o1) < 0);
      assertTrue("All nodes: "+list,comparator.compare(a, o2) < 0);
      assertTrue("All nodes: "+list,comparator.compare(b, o1) < 0);
      assertTrue("All nodes: "+list,comparator.compare(b, o2) < 0);
      assertTrue("All nodes: "+list,comparator.compare(c, o1) < 0);
      assertTrue("All nodes: "+list,comparator.compare(c, o2) < 0);
    }
  }
}
