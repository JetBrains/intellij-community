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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: Feb 11, 2005
 */
public class ShortestPathTest extends GraphTestCase {
  public void testEmptyPath() {
    final HashMap<String, String> graph = new HashMap<>();
    graph.put("a", "");
    graph.put("b", "");
    doTest(graph, "a", "a", "a");
  }

  public void testNoPath() {
    final HashMap<String, String> graph = new HashMap<>();
    graph.put("a", "c");
    graph.put("b", "a");
    graph.put("c", "a");
    assertNull(getShortestPath(graph, "a", "b"));
  }

  public void test1() {
    final HashMap<String, String> graph = new HashMap<>();
    graph.put("a", "");
    graph.put("b", "ac");
    graph.put("c", "ab");
    graph.put("d", "a");
    doTest(graph, "b", "a", "ba");
  }

  public void test2() {
    final HashMap<String, String> graph = new HashMap<>();
    graph.put("a", "cd");
    graph.put("b", "a");
    graph.put("c", "d");
    graph.put("d", "ab");
    doTest(graph, "c", "b", "cdb");
  }

  public void test3() {
    final HashMap<String, String> graph = new HashMap<>();
    graph.put("a", "bd");
    graph.put("b", "d");
    graph.put("c", "a");
    graph.put("d", "ac");
    doTest(graph, "b", "c", "bdc");
  }

  public void test4() {
    final HashMap<String, String> graph = new HashMap<>();
    graph.put("a", "be");
    graph.put("b", "d");
    graph.put("c", "a");
    graph.put("d", "ef");
    graph.put("e", "c");
    graph.put("f", "e");
    doTest(graph, "b", "c", "bdec");
  }

  private static void doTest(HashMap<String, String> graph, final String from, final String to, final String expectedPath) {
    final List<String> shortestPath = getShortestPath(graph, from, to);
    assertNotNull(shortestPath);
    assertEquals(expectedPath, StringUtil.join(shortestPath, ""));
  }

  @Nullable
  private static List<String> getShortestPath(Map<String, String> graph, final String from, final String to) {
    return getAlgorithmsInstance().findShortestPath(initGraph(graph), from, to);
  }
}
