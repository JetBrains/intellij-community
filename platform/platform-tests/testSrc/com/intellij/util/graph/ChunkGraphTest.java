// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph;

import com.intellij.util.Chunk;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertTrue;

/**
 * @author Eugene Zhuravlev
 */
public class ChunkGraphTest extends GraphTestCase {
  @Test
  public void testGraph1() {
    final Map<String, String> arcs = new HashMap<>();
    arcs.put("a", "b");
    arcs.put("b", "c");
    arcs.put("c", "bd");
    arcs.put("d", "e");
    arcs.put("e", "d");

    final Graph<Chunk<String>> graph = getAlgorithmsInstance().computeSCCGraph(initGraph(arcs));

    final List<Chunk<String>> expectedNodes = new ArrayList<>();
    Chunk<String> A = new Chunk<>("a");
    expectedNodes.add(A);
    Chunk<String> BC = new Chunk<>(ContainerUtil.newHashSet("b", "c"));
    expectedNodes.add(BC);
    Chunk<String> DE = new Chunk<>(ContainerUtil.newHashSet("d", "e"));
    expectedNodes.add(DE);

    checkVertices(expectedNodes, graph.getNodes());

    final Map<Chunk<String>, Set<Chunk<String>>> expectedArcs = new HashMap<>();
    expectedArcs.put(A, new HashSet<>());
    expectedArcs.put(BC, ContainerUtil.newHashSet(A));
    expectedArcs.put(DE, ContainerUtil.newHashSet(BC));

    checkArcs(expectedArcs, graph);
  }

  @Test
  public void testGraph2() {
    final Map<String, String> arcs = new HashMap<>();
    arcs.put("a", "b");
    arcs.put("b", "ac");
    arcs.put("c", "ad");
    arcs.put("d", "");

    final Graph<Chunk<String>> graph = getAlgorithmsInstance().computeSCCGraph(initGraph(arcs));

    final List<Chunk<String>> expectedNodes = new ArrayList<>();
    Chunk<String> ABC = new Chunk<>(ContainerUtil.newHashSet("a", "b", "c"));
    expectedNodes.add(ABC);
    Chunk<String> D = new Chunk<>("d");
    expectedNodes.add(D);

    checkVertices(expectedNodes, graph.getNodes());

    final Map<Chunk<String>, Set<Chunk<String>>> expectedArcs = new HashMap<>();
    expectedArcs.put(ABC, new HashSet<>());
    expectedArcs.put(D, ContainerUtil.newHashSet(ABC));

    checkArcs(expectedArcs, graph);
  }

  private static void checkArcs(Map<Chunk<String>, Set<Chunk<String>>> expectedArcs, Graph<Chunk<String>> graph) {
    for (Chunk<String> chunk : graph.getNodes()) {
      List<Chunk<String>> ins = ContainerUtil.newArrayList(() -> graph.getIn(chunk));
      Set<Chunk<String>> expectedIns = expectedArcs.get(chunk);
      assertTrue(expectedIns.size() == ins.size());
      assertTrue(expectedIns.equals(new HashSet<>(ins)));
    }
  }

  private static void checkVertices(List<Chunk<String>> expected, Iterable<Chunk<String>> nodes) {
    List<Chunk<String>> realNodes = ContainerUtil.newArrayList(nodes);
    assertTrue(expected.size() == realNodes.size());
    assertTrue(new HashSet<>(expected).equals(new HashSet<>(realNodes)));
  }
}