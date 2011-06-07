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

import com.intellij.util.graph.impl.CycleFinder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;

import java.util.*;

/**
 * User: anna
 * Date: Feb 13, 2005
 */
public class SearchCyclesTest extends GraphTestCase {
  public void test1() throws Exception{
    final HashMap<String, String> graph = new HashMap<String, String>();
    graph.put("a", "bd");
    graph.put("b", "d");
    graph.put("c", "a");
    graph.put("d", "ac");

    doTest(graph, "a", "da", "bdca");
  }

  public void test2() throws Exception{
    final HashMap<String, String> graph = new HashMap<String, String>();
    graph.put("a", "b");
    graph.put("b", "d");
    graph.put("c", "a");
    graph.put("d", "ce");
    graph.put("e", "d");

    doTest(graph, "a", "bdca");
  }

  public void test3() throws Exception{
    final HashMap<String, String> graph = new HashMap<String, String>();
    graph.put("a", "bd");
    graph.put("b", "d");
    graph.put("d", "a");

    doTest(graph, "a", "da");
  }

  public void test4() throws Exception {
    final HashMap<String, String> graph = new HashMap<String, String>();
    graph.put("a", "b");
    graph.put("b", "d");
    graph.put("c", "a");
    graph.put("d", "ef");
    graph.put("e", "c");
    graph.put("f", "e");

    doTest(graph, "a", "bdeca");
  }

  public void test5() throws Exception{
    final HashMap<String, String> graph = new HashMap<String, String>();
    graph.put("a", "be");
    graph.put("b", "d");
    graph.put("c", "a");
    graph.put("d", "ef");
    graph.put("e", "c");
    graph.put("f", "e");
    doTest(graph, "a", "bdeca", "eca");
  }

  private static void doTest(HashMap<String, String> graph, final String node, String... expected) {
    Graph<String> stringGraph = initGraph(graph);
    final Set<List<String>> nodeCycles = new CycleFinder<String>(stringGraph).getNodeCycles(node);
    checkResult(expected, nodeCycles);
  }

  private static void checkResult(String[] expected, Set<List<String>> cycles) {
    Set<String> cycleStrings = new HashSet<String>();
    for (List<String> cycle : cycles) {
      cycleStrings.add(StringUtil.join(cycle, ""));
    }
    UsefulTestCase.assertSameElements(cycleStrings, expected);
  }
}
