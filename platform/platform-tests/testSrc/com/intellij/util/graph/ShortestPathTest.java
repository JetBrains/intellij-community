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
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author anna
 * @since Feb 11, 2005
 */
public class ShortestPathTest extends GraphTestCase {
  @Test
  public void testEmptyPath() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("a", "");
    graph.put("b", "");
    doTest(graph, "a", "a", "a");
  }

  @Test
  public void testNoPath() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("a", "c");
    graph.put("b", "a");
    graph.put("c", "a");
    doTest(graph, "a", "b", null);
  }

  @Test
  public void test1() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("a", "");
    graph.put("b", "ac");
    graph.put("c", "ab");
    graph.put("d", "a");
    doTest(graph, "b", "a", "ba");
  }

  @Test
  public void test2() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("a", "cd");
    graph.put("b", "a");
    graph.put("c", "d");
    graph.put("d", "ab");
    doTest(graph, "c", "b", "cdb");
  }

  @Test
  public void test3() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("a", "bd");
    graph.put("b", "d");
    graph.put("c", "a");
    graph.put("d", "ac");
    doTest(graph, "b", "c", "bdc");
  }

  @Test
  public void test4() {
    final Map<String, String> graph = new HashMap<>();
    graph.put("a", "be");
    graph.put("b", "d");
    graph.put("c", "a");
    graph.put("d", "ef");
    graph.put("e", "c");
    graph.put("f", "e");
    doTest(graph, "b", "c", "bdec");
  }

  private static void doTest(Map<String, String> graph, String from, String to, String expectedPath) {
    List<String> shortestPath = getAlgorithmsInstance().findShortestPath(initGraph(graph), from, to);
    if (expectedPath != null) {
      assertNotNull(shortestPath);
      assertEquals(expectedPath, StringUtil.join(shortestPath, ""));
    }
    else {
      assertNull(shortestPath);
    }
  }
}