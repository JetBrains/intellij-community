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

import com.intellij.openapi.util.text.StringUtil;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author anna
 * @since Feb 13, 2005
 */
public class SearchCyclesTest extends GraphTestCase {
  @Test
  public void test1() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("a", "bd");
    graph.put("b", "d");
    graph.put("c", "a");
    graph.put("d", "ac");

    doTest(graph, "a", "da", "bdca");
  }

  @Test
  public void test2() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("a", "b");
    graph.put("b", "d");
    graph.put("c", "a");
    graph.put("d", "ce");
    graph.put("e", "d");

    doTest(graph, "a", "bdca");
  }

  @Test
  public void test3() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("a", "bd");
    graph.put("b", "d");
    graph.put("d", "a");

    doTest(graph, "a", "da");
  }

  @Test
  public void test4() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("a", "b");
    graph.put("b", "d");
    graph.put("c", "a");
    graph.put("d", "ef");
    graph.put("e", "c");
    graph.put("f", "e");

    doTest(graph, "a", "bdeca");
  }

  @Test
  public void test5() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("a", "be");
    graph.put("b", "d");
    graph.put("c", "a");
    graph.put("d", "ef");
    graph.put("e", "c");
    graph.put("f", "e");
    doTest(graph, "a", "bdeca", "eca");
  }

  private static void doTest(Map<String, String> graph, String node, String... expected) {
    Set<String> cycles = getAlgorithmsInstance().findCycles(initGraph(graph), node).stream()
      .map(cycle -> StringUtil.join(cycle, ""))
      .collect(Collectors.toSet());
    assertThat(cycles).containsExactlyInAnyOrder(expected);
  }
}