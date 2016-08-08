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
    Chunk<String> BC = new Chunk<>(toSet("b", "c"));
    expectedNodes.add(BC);
    Chunk<String> DE = new Chunk<>(toSet("d", "e"));
    expectedNodes.add(DE);

    checkVertices(expectedNodes, graph.getNodes().iterator());

    final Map<Chunk<String>, Set<Chunk<String>>> expectedArcs = new HashMap<>();
    expectedArcs.put(A, toSet());
    expectedArcs.put(BC, toSet(A));
    expectedArcs.put(DE, toSet(BC));

    checkArcs(expectedArcs, graph);
  }

  public void testGraph2() {
    final Map<String, String> arcs = new HashMap<>();
    arcs.put("a", "b");
    arcs.put("b", "ac");
    arcs.put("c", "ad");
    arcs.put("d", "");

    final Graph<Chunk<String>> graph = getAlgorithmsInstance().computeSCCGraph(initGraph(arcs));

    final List<Chunk<String>> expectedNodes = new ArrayList<>();
    Chunk<String> ABC = new Chunk<>(toSet("a", "b", "c"));
    expectedNodes.add(ABC);
    Chunk<String> D = new Chunk<>("d");
    expectedNodes.add(D);

    checkVertices(expectedNodes, graph.getNodes().iterator());

    final Map<Chunk<String>, Set<Chunk<String>>> expectedArcs = new HashMap<>();
    expectedArcs.put(ABC, toSet());
    expectedArcs.put(D, toSet(ABC));

    checkArcs(expectedArcs, graph);
  }

  private static void checkArcs(Map<Chunk<String>, Set<Chunk<String>>> expectedArcs, Graph<Chunk<String>> graph) {
    for (Chunk<String> chunk : graph.getNodes()) {
      final List<Chunk<String>> ins = new ArrayList<>();
      final Iterator<Chunk<String>> insIterator = graph.getIn(chunk);
      while (insIterator.hasNext()) {
        ins.add(insIterator.next());
      }
      final Set<Chunk<String>> expectedIns = expectedArcs.get(chunk);
      assertTrue(expectedIns.size() == ins.size());
      assertTrue(expectedIns.equals(new HashSet<>(ins)));
    }
  }

  private static <T> Set<T> toSet(T... strings) {
    return new HashSet<>(Arrays.asList(strings));
  }

  private static Set<Chunk<String>> toSet() {
    return new HashSet<>();
  }

  private static Set<Chunk<String>> toSet(Chunk<String> c) {
    return Collections.singleton(c);
  }


  private static void checkVertices(List<Chunk<String>> expected, Iterator<Chunk<String>> nodes) {
    List<Chunk<String>> realNodes = new ArrayList<>();
    while (nodes.hasNext()) {
      realNodes.add(nodes.next());
    }
    assertTrue(expected.size() == realNodes.size());
    assertTrue(new HashSet<>(expected).equals(new HashSet<>(realNodes)));
  }
}
