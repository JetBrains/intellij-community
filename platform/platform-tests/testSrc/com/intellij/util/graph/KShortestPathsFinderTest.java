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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.junit.Test;

import java.util.*;

public class KShortestPathsFinderTest extends GraphTestCase {
  @Test
  public void testEmpty() {
    Map<String, String> graph = new HashMap<>();
    graph.put("s", "");
    graph.put("t", "");
    doTest(graph);
  }

  @Test
  public void testOneEdge() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("s", "t");
    graph.put("t", "");
    doTest(graph, "st");
  }

  @Test
  public void testNoPaths() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("s", "a");
    graph.put("a", "s");
    graph.put("b", "at");
    graph.put("t", "sab");
    doTest(graph);
  }

  @Test
  public void testOneVertex() {
    Map<String, String> graph = new HashMap<>();
    graph.put("s", "");
    doTest(graph, "s", "s", 5, "s");
  }

  @Test
  public void testTwoPaths() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("s", "ta");
    graph.put("a", "t");
    graph.put("t", "");
    doTest(graph, "st", "sat");
  }

  @Test
  public void testManyEdgesToTarget() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("s", "a");
    graph.put("a", "bt");
    graph.put("b", "ct");
    graph.put("c", "dt");
    graph.put("d", "t");
    graph.put("t", "");
    doTest(graph, "sat", "sabt", "sabct", "sabcdt");
  }

  @Test
  public void testManyEdgesFromSource() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("s", "abcdt");
    graph.put("a", "b");
    graph.put("b", "c");
    graph.put("c", "d");
    graph.put("d", "t");
    graph.put("t", "");
    doTest(graph, "st", "sdt", "scdt", "sbcdt", "sabcdt");
  }

  @Test
  public void testTwoParts() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("s", "ab");
    graph.put("a", "b");
    graph.put("b", "cd");
    graph.put("c", "t");
    graph.put("d", "e");
    graph.put("e", "t");
    graph.put("t", "");
    doTest(graph, "sbct", "sabct", "sbdet", "sabdet");
  }

  @Test
  public void testHangingEdges() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("s", "ae");
    graph.put("a", "bc");
    graph.put("b", "ac");
    graph.put("c", "ab");
    graph.put("d", "s");
    graph.put("e", "t");
    graph.put("t", "");
    doTest(graph, "set");
  }

  @Test
  public void testSimpleCycle() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("s", "t");
    graph.put("t", "s");
    doTest(graph, 4, "st", "stst", "ststst", "stststst");
  }

  @Test
  public void testComplexCycle() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("s", "p");
    graph.put("p", "qt");
    graph.put("q", "vt");
    graph.put("v", "p");
    graph.put("t", "");
    doTest(graph, 5, "spt", "spqt", "spqvpt", "spqvpqt", "spqvpqvpt");
  }

  @Test
  public void testHeap() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("s", "a");
    graph.put("a", "bd");
    graph.put("b", "cd");
    graph.put("c", "td");
    graph.put("d", "e");
    graph.put("e", "f");
    graph.put("f", "t");
    graph.put("t", "");
    doTest(graph, "sabct", "sadeft", "sabdeft", "sabcdeft");
  }

  @Test
  public void testBigHeap() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("s", "lo");
    graph.put("a", "tb");
    graph.put("b", "t");
    graph.put("c", "ad");
    graph.put("d", "e");
    graph.put("e", "b");
    graph.put("f", "cg");
    graph.put("g", "h");
    graph.put("h", "d");
    graph.put("i", "fj");
    graph.put("j", "k");
    graph.put("k", "g");
    graph.put("l", "im");
    graph.put("m", "n");
    graph.put("n", "j");
    graph.put("o", "p");
    graph.put("p", "m");
    graph.put("t", "");
    doTest(graph, "slifcat", "slifcabt", "slifcdebt", "slifghdebt", "slijkghdebt", "slmnjkghdebt", "sopmnjkghdebt");
  }

  private static void doTest(Map<String, String> graph, String... expectedPaths) {
    doTest(graph, 10, expectedPaths);
  }

  private static void doTest(Map<String, String> graph, final int k, String... expectedPaths) {
    doTest(graph, "s", "t", k, expectedPaths);
  }

  private static void doTest(Map<String, String> graph, final String start, final String finish, final int k, String... expectedPaths) {
    Graph<String> generator = initGraph(graph);
    List<List<String>> paths = getAlgorithmsInstance().findKShortestPaths(generator, start, finish, k, new EmptyProgressIndicator(ModalityState.NON_MODAL));
    List<String> pathStrings = new ArrayList<>();
    Set<Integer> sizes = new HashSet<>();
    for (List<String> path : paths) {
      pathStrings.add(StringUtil.join(path, ""));
      sizes.add(path.size());
    }
    if (sizes.size() != paths.size()) {
      UsefulTestCase.assertSameElements(pathStrings, expectedPaths);
    }
    else {
      UsefulTestCase.assertOrderedEquals(pathStrings, expectedPaths);
    }
  }
}