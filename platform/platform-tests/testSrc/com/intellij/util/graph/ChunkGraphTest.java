/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.graph;

import com.intellij.util.Chunk;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 27, 2004
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class ChunkGraphTest extends GraphTestCase {

  public void testGraph1() {
    final Map<String, String> arcs = new HashMap<String, String>();
    arcs.put("a", "b");
    arcs.put("b", "c");
    arcs.put("c", "bd");
    arcs.put("d", "e");
    arcs.put("e", "d");

    final Graph<Chunk<String>> graph = getAlgorithmsInstance().computeSCCGraph(initGraph(arcs));

    final List<Chunk<String>> expectedNodes = new ArrayList<Chunk<String>>();
    Chunk<String> A = new Chunk<String>("a");
    expectedNodes.add(A);
    Chunk<String> BC = new Chunk<String>(toSet("b", "c"));
    expectedNodes.add(BC);
    Chunk<String> DE = new Chunk<String>(toSet("d", "e"));
    expectedNodes.add(DE);

    checkVertices(expectedNodes, graph.getNodes().iterator());

    final Map<Chunk<String>, Set<Chunk<String>>> expectedArcs = new HashMap<Chunk<String>, Set<Chunk<String>>>();
    expectedArcs.put(A, toSet());
    expectedArcs.put(BC, toSet(A));
    expectedArcs.put(DE, toSet(BC));

    checkArcs(expectedArcs, graph);
  }

  public void testGraph2() {
    final Map<String, String> arcs = new HashMap<String, String>();
    arcs.put("a", "b");
    arcs.put("b", "ac");
    arcs.put("c", "ad");
    arcs.put("d", "");

    final Graph<Chunk<String>> graph = getAlgorithmsInstance().computeSCCGraph(initGraph(arcs));

    final List<Chunk<String>> expectedNodes = new ArrayList<Chunk<String>>();
    Chunk<String> ABC = new Chunk<String>(toSet("a", "b", "c"));
    expectedNodes.add(ABC);
    Chunk<String> D = new Chunk<String>("d");
    expectedNodes.add(D);

    checkVertices(expectedNodes, graph.getNodes().iterator());

    final Map<Chunk<String>, Set<Chunk<String>>> expectedArcs = new HashMap<Chunk<String>, Set<Chunk<String>>>();
    expectedArcs.put(ABC, toSet());
    expectedArcs.put(D, toSet(ABC));

    checkArcs(expectedArcs, graph);
  }

  private static void checkArcs(Map<Chunk<String>, Set<Chunk<String>>> expectedArcs, Graph<Chunk<String>> graph) {
    for (Chunk<String> chunk : graph.getNodes()) {
      final List<Chunk<String>> ins = new ArrayList<Chunk<String>>();
      final Iterator<Chunk<String>> insIterator = graph.getIn(chunk);
      while (insIterator.hasNext()) {
        ins.add(insIterator.next());
      }
      final Set<Chunk<String>> expectedIns = expectedArcs.get(chunk);
      assertTrue(expectedIns.size() == ins.size());
      assertTrue(expectedIns.equals(new HashSet<Chunk<String>>(ins)));
    }
  }

  private static <T> Set<T> toSet(T... strings) {
    return new HashSet<T>(Arrays.asList(strings));
  }

  private static Set<Chunk<String>> toSet() {
    return new HashSet<Chunk<String>>();
  }

  private static Set<Chunk<String>> toSet(Chunk<String> c) {
    return Collections.singleton(c);
  }


  private static void checkVertices(List<Chunk<String>> expected, Iterator<Chunk<String>> nodes) {
    List<Chunk<String>> realNodes = new ArrayList<Chunk<String>>();
    while (nodes.hasNext()) {
      realNodes.add(nodes.next());
    }
    assertTrue(expected.size() == realNodes.size());
    assertTrue(new HashSet<Chunk<String>>(expected).equals(new HashSet<Chunk<String>>(realNodes)));
  }
}
