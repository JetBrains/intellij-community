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
package com.intellij.util.graph;

import com.intellij.util.ObjectUtils;
import com.intellij.util.graph.impl.GraphAlgorithmsImpl;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * @author dsl
 */
public class DFSTBuilderTest {
  @Test
  public void testGraph() {
    TestNode nA = new TestNode("A");
    TestNode nB = new TestNode("B");
    TestNode nC = new TestNode("C");
    TestNode nD = new TestNode("D");
    TestNode nE = new TestNode("E");
    TestNode nF = new TestNode("F");
    TestNode[] allNodes = {nA, nB, nC, nD, nE, nF};
    Map<TestNode, TestNode[]> map = new HashMap<>();
    map.put(nA, new TestNode[0]);
    map.put(nB, new TestNode[0]);
    map.put(nC, new TestNode[]{nA, nB});
    map.put(nD, new TestNode[]{nA, nC});
    map.put(nE, new TestNode[]{nC});
    map.put(nF, new TestNode[]{nB});

    Graph<TestNode> graph = graphByNodes(allNodes, map);
    DFSTBuilder<TestNode> builder = new DFSTBuilder<>(graph);
    if (!builder.isAcyclic()) {
      fail("Acyclic graph expected");
      return;
    }

    Comparator<TestNode> comparator = builder.comparator();
    assertTrue(comparator.compare(nA, nC) < 0);
    assertTrue(comparator.compare(nB, nC) < 0);
    assertTrue(comparator.compare(nA, nD) < 0);
    assertTrue(comparator.compare(nC, nD) < 0);
    assertTrue(comparator.compare(nC, nE) < 0);
    assertTrue(comparator.compare(nB, nF) < 0);
  }

  private static Graph<TestNode> graphByNodes(TestNode[] allNodes, Map<TestNode, TestNode[]> mapIn) {
    return GraphGenerator.generate(new InboundSemiGraph<TestNode>() {
      @Override
      public Collection<TestNode> getNodes() {
        return Arrays.asList(allNodes);
      }

      @Override
      public Iterator<TestNode> getIn(TestNode n) {
        return GraphTestUtil.iteratorOfArray(ObjectUtils.notNull(mapIn.get(n), new TestNode[0]));
      }
    });
  }

  @Test
  public void testCircularDependency() {
    TestNode nA = new TestNode("A");
    TestNode nB = new TestNode("B");
    TestNode nC = new TestNode("C");
    TestNode nD = new TestNode("D");
    TestNode[] allNodes = {nA, nB, nC, nD};
    Map<TestNode, TestNode[]> map = new HashMap<>();
    map.put(nA, new TestNode[0]);
    map.put(nB, new TestNode[0]);
    map.put(nC, new TestNode[]{nA, nB, nD});
    map.put(nD, new TestNode[]{nC, nB});
    checkCircularDependencyDetected(allNodes, map);
  }

  private static void checkCircularDependencyDetected(TestNode[] allNodes, Map<TestNode, TestNode[]> map) {
    Graph<TestNode> graph = graphByNodes(allNodes, map);
    DFSTBuilder<TestNode> builder = new DFSTBuilder<>(graph);
    assertTrue(builder.getCircularDependency() != null);
  }

  @Test
  public void testCircularDependency2() {
    TestNode nA = new TestNode("A");
    TestNode nB = new TestNode("B");
    TestNode[] allNodes = {nA, nB};
    Map<TestNode, TestNode[]> map = new HashMap<>();
    map.put(nA, new TestNode[]{nB});
    map.put(nB, new TestNode[]{nA});
    checkCircularDependencyDetected(allNodes, map);
  }

  @Test
  public void testCircularDependency3() {
    TestNode nA = new TestNode("A");
    TestNode nB = new TestNode("B");
    TestNode nC = new TestNode("C");
    TestNode[] allNodes = {nA, nB, nC};
    Map<TestNode, TestNode[]> map = new HashMap<>();
    map.put(nA, new TestNode[]{nB});
    map.put(nB, new TestNode[]{nA});
    map.put(nC, new TestNode[0]);
    checkCircularDependencyDetected(allNodes, map);
  }

  @Test
  public void testTNumberingSimple() {
    TestNode nA = new TestNode("A");
    TestNode nB = new TestNode("B");
    TestNode nC = new TestNode("C");
    TestNode nD = new TestNode("D");
    TestNode[] allNodes = {nA, nB, nC, nD};
    Map<TestNode, TestNode[]> map = new HashMap<>();
    map.put(nA, new TestNode[]{nC});
    map.put(nB, new TestNode[]{nA});
    map.put(nC, new TestNode[]{nB});
    map.put(nD, new TestNode[]{nB});
    Graph<TestNode> graph = graphByNodes(allNodes, map);
    DFSTBuilder<TestNode> builder = new DFSTBuilder<>(graph);
    assertFalse(builder.isAcyclic());
    Comparator<TestNode> comparator = builder.comparator();
    assertTrue(comparator.compare(nA, nD) < 0);
    assertTrue(comparator.compare(nB, nD) < 0);
    assertTrue(comparator.compare(nC, nD) < 0);
  }

  @Test
  public void testStackOverflow() {
    TestNode[] allNodes = new TestNode[10000];
    Map<TestNode, TestNode[]> map = new HashMap<>();
    for (int i = 0; i < allNodes.length; i++) {
      allNodes[i] = new TestNode(String.valueOf(i));
      if (i != 0) {
        map.put(allNodes[i], new TestNode[]{allNodes[i - 1]});
      }
    }
    map.put(allNodes[0], new TestNode[]{allNodes[allNodes.length - 1]});
    Graph<TestNode> graph = graphByNodes(allNodes, map);
    DFSTBuilder<TestNode> builder = new DFSTBuilder<>(graph);
    assertFalse(builder.isAcyclic());
  }

  @Test
  public void testComponentsReportedInLoadingOrder() {
    TestNode main = new TestNode("main");
    TestNode dep = new TestNode("dep");
    TestNode d = new TestNode("d");
    TestNode d2 = new TestNode("d2");
    TestNode resMain = new TestNode("resMain");
    TestNode resDep = new TestNode("resDep");
    TestNode[] allNodes = {main, dep, d, d2, resMain, resDep};
    Map<TestNode, TestNode[]> mapIn = new HashMap<>();
    mapIn.put(main, new TestNode[]{d, resMain});
    mapIn.put(dep, new TestNode[]{main, resDep});
    mapIn.put(d, new TestNode[]{d2});
    mapIn.put(d2, new TestNode[]{dep, d});
    Graph<TestNode> graph = graphByNodes(allNodes, mapIn);

    DFSTBuilder<TestNode> builder = new DFSTBuilder<>(graph);
    assertTrue(!builder.isAcyclic());
    Comparator<TestNode> comparator = builder.comparator();
    assertTrue(comparator.compare(resMain, main) < 0);
    assertTrue(comparator.compare(resMain, d) < 0);
    assertTrue(comparator.compare(resMain, d2) < 0);
    assertTrue(comparator.compare(resDep, dep) < 0);
    assertTrue(comparator.compare(resMain, resDep) > 0); //reversed loading order
  }

  @Test
  public void testReportedInLoadingOrder() {
    TestNode o = new TestNode("o");
    TestNode a = new TestNode("a");
    TestNode b = new TestNode("b");
    TestNode c = new TestNode("c");
    for (int oIndex = 0; oIndex < 4; oIndex++) {
      List<TestNode> list = new ArrayList<>(Arrays.asList(a, b, c));
      list.add(oIndex, o);
      TestNode[] allNodes = list.toArray(new TestNode[0]);

      Map<TestNode, TestNode[]> mapIn = new HashMap<>();
      mapIn.put(o, new TestNode[]{a, b, c});

      DFSTBuilder<TestNode> builder = new DFSTBuilder<>(graphByNodes(allNodes, mapIn));
      assertTrue(builder.isAcyclic());
      Comparator<TestNode> comparator = builder.comparator();
      TestNode[] sorted = allNodes.clone();
      Arrays.sort(sorted, comparator);
      assertEquals("All nodes: " + list, Arrays.asList(c, b, a, o), Arrays.asList(sorted));
    }
  }

  @Test
  public void testSccReportedInLoadingOrder() {
    TestNode o1 = new TestNode("o1");
    TestNode o2 = new TestNode("o2");
    TestNode a = new TestNode("a");
    TestNode b = new TestNode("b");
    TestNode c = new TestNode("c");
    for (int oIndex = 0; oIndex < 4; oIndex++) {
      List<TestNode> list = new ArrayList<>(Arrays.asList(a, b, c));
      list.add(oIndex, o1);
      list.add(oIndex, o2);
      TestNode[] allNodes = list.toArray(new TestNode[0]);

      Map<TestNode, TestNode[]> mapIn = new HashMap<>();
      mapIn.put(o1, new TestNode[]{a, b, c, o2});
      mapIn.put(o2, new TestNode[]{o1});

      DFSTBuilder<TestNode> builder = new DFSTBuilder<>(graphByNodes(allNodes, mapIn));
      assertFalse(builder.isAcyclic());
      Comparator<TestNode> comparator = builder.comparator();
      assertTrue("All nodes: " + list, comparator.compare(b, a) < 0); //reversed loading order
      assertTrue("All nodes: " + list, comparator.compare(c, a) < 0); //reversed loading order
      assertTrue("All nodes: " + list, comparator.compare(c, b) < 0); //reversed loading order
      assertTrue("All nodes: " + list, comparator.compare(a, o1) < 0);
      assertTrue("All nodes: " + list, comparator.compare(a, o2) < 0);
      assertTrue("All nodes: " + list, comparator.compare(b, o1) < 0);
      assertTrue("All nodes: " + list, comparator.compare(b, o2) < 0);
      assertTrue("All nodes: " + list, comparator.compare(c, o1) < 0);
      assertTrue("All nodes: " + list, comparator.compare(c, o2) < 0);
    }
  }

  @Test
  public void testOrderingWithSelectedEntryPoint() {
    TestNode a = new TestNode("a");
    TestNode b = new TestNode("b");
    TestNode c = new TestNode("c");
    TestNode d = new TestNode("d");
    TestNode e = new TestNode("e");
    TestNode f = new TestNode("f");
    TestNode g = new TestNode("g");
    List<TestNode> allNodes = Arrays.asList(a, b, c, d, e, f, g);
    Map<TestNode, TestNode[]> mapIn = new HashMap<>();
    mapIn.put(a, new TestNode[]{b});
    mapIn.put(b, new TestNode[]{d, c});
    mapIn.put(c, new TestNode[]{d, b});
    mapIn.put(d, new TestNode[]{f, e});
    mapIn.put(e, new TestNode[]{f, d});
    mapIn.put(f, new TestNode[]{g, b});
    for (int seed = 0; seed < 4; ++seed) {
      Random random = new Random(seed);
      List<TestNode> shuffled = new ArrayList<>(allNodes);
      Collections.shuffle(shuffled, random);
      Graph<TestNode> graph = new GraphAlgorithmsImpl().invertEdgeDirections(graphByNodes(shuffled.toArray(new TestNode[0]), mapIn));
      DFSTBuilder<TestNode> builder = new DFSTBuilder<>(graph, a);
      Comparator<TestNode> comparator = builder.comparator(true);
      for (int i = 0; i < allNodes.size() -1; ++i) {
        assertTrue(comparator.compare(allNodes.get(i), allNodes.get(i + 1)) < 0);
      }
    }
  }
}