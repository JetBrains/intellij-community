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

import com.intellij.util.Chunk;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertTrue;

/**
 * @author Eugene Zhuravlev
 * @since Sep 27, 2004
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
    expectedArcs.put(A, ContainerUtil.newHashSet());
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
    expectedArcs.put(ABC, ContainerUtil.newHashSet());
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