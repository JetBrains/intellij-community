// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph;

import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectTest extends GraphTestCase {

  @Test
  public void testSimple() {
    Map<String, String> graph = new THashMap<>();
    graph.put("a", "b");
    graph.put("b", "c");
    graph.put("c", "c");
    doTest(graph, "b", "b", "c");
  }

  @Test
  public void testFull() {
    Map<String, String> graph = new THashMap<>();
    graph.put("a", "bcd");
    graph.put("b", "acd");
    graph.put("c", "abd");
    graph.put("d", "abc");
    doTest(graph, "a", "a", "b", "c", "d");
  }

  @Test
  public void testVisitedNode() {
    Map<String, String> graph = new THashMap<>();
    graph.put("a", "bcd");
    graph.put("b", "acd");
    graph.put("c", "abd");
    graph.put("d", "abc");
    Set<String> nodes = new THashSet<>();
    nodes.add("a");
    getAlgorithmsInstance().collectOutsRecursively(initGraph(graph), "a", nodes);
    assertThat(nodes.size()).isEqualTo(1);
  }

  @Test
  public void testBigGraph() {
    Map<String, String> graph = new THashMap<>();
    List<String> answer = new ArrayList<>();
    for (int i = 0; i < 10000; ++i) {
      graph.put(String.valueOf((char)i), String.valueOf((char)(i + 1)));
      answer.add(String.valueOf((char)i));
    }
    graph.put(String.valueOf((char)10000), String.valueOf((char)0));
    answer.add(String.valueOf((char)10000));
    doTest(graph, "0", ArrayUtil.toStringArray(answer));
  }

  private static void doTest(final @NotNull Map<String, String> graph,
                             final @NotNull String node,
                             final @NotNull String... expected) {
    Set<String> nodes = new THashSet<>();
    getAlgorithmsInstance().collectOutsRecursively(initGraph(graph), node, nodes);
    assertThat(nodes).containsExactlyInAnyOrder(expected);
  }
}
