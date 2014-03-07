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
package com.intellij.vcs.log.newgraph.render.cell;

import com.intellij.util.containers.SLRUMap;
import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.MutableGraph;
import com.intellij.vcs.log.newgraph.gpaph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class EdgesInRow {
  private final static int CACHE_SIZE = 100;
  private final static int WALK_SIZE = 1000;

  @NotNull
  private final MutableGraph myGraph;

  @NotNull
  private final SLRUMap<Integer, Set<Edge>> cache = new SLRUMap<Integer, Set<Edge>>(CACHE_SIZE, CACHE_SIZE * 2);

  public EdgesInRow(@NotNull MutableGraph graph) {
    myGraph = graph;
  }

  @NotNull
  public Set<Edge> getEdgesInRow(int visibleRowIndex) {
    Set<Edge> edges = cache.get(visibleRowIndex);
    if (edges != null) {
      return edges;
    }

    return calculateEdgesAround(visibleRowIndex);
  }

  public void invalidate() {
    cache.clear();
  }

  @NotNull
  private Set<Edge> calculateEdgesAround(int visibleRowIndex) {
    int startIndex = Math.max(visibleRowIndex - CACHE_SIZE / 2, 0);
    int endIndex = Math.min(visibleRowIndex + CACHE_SIZE / 2, myGraph.getCountVisibleNodes() - 1);
    List<Set<Edge>> uCorrectEdges = getUCorrectEdges(startIndex, endIndex);
    List<Set<Edge>> dCorrectEdges = getDCorrectEdges(startIndex, endIndex);

    assert uCorrectEdges.size() == dCorrectEdges.size();

    for (int i = 0; i < uCorrectEdges.size(); i++)
      uCorrectEdges.get(i).addAll(dCorrectEdges.get(i));

    for (int i = 0; i < uCorrectEdges.size(); i++)
      cache.put(startIndex + i, uCorrectEdges.get(i));

    return uCorrectEdges.get(visibleRowIndex - startIndex);
  }

  // [start, end]
  @NotNull
  private List<Set<Edge>> getUCorrectEdges(int startIndex, int endIndex) {
    int startCalculateIndex = Math.max(startIndex - WALK_SIZE, 0);
    List<Set<Edge>> result = new ArrayList<Set<Edge>>(endIndex - startIndex + 1);
    Node currentNode = myGraph.getNode(startCalculateIndex);
    Set<Edge> edgesInCurrentRow = new HashSet<Edge>();

    if (startCalculateIndex >= startIndex)
      result.add(new HashSet<Edge>(edgesInCurrentRow));

    for (int i = startCalculateIndex + 1; i <= endIndex; i++) {
      Node nextNode = myGraph.getNode(i);
      oneDownStep(edgesInCurrentRow, currentNode, nextNode);

      if (i >= startIndex)
        result.add(new HashSet<Edge>(edgesInCurrentRow));

      currentNode = nextNode;
    }
    return result;
  }

  private static void oneDownStep(Set<Edge> edgesInCurrentRow, Node currentNode, Node nextNode) {
    edgesInCurrentRow.addAll(currentNode.getDownEdges());
    edgesInCurrentRow.removeAll(nextNode.getUpEdges());
  }

  // [start, end]
  @NotNull
  private List<Set<Edge>> getDCorrectEdges(int startIndex, int endIndex) {
    int endCalculateIndex = Math.min(endIndex + WALK_SIZE, myGraph.getCountVisibleNodes() - 1);
    List<Set<Edge>> result = new ArrayList<Set<Edge>>(endIndex - startIndex + 1);
    Node currentNode = myGraph.getNode(endCalculateIndex);
    Set<Edge> edgesInCurrentRow = new HashSet<Edge>();

    if (endCalculateIndex <= endIndex)
      result.add(new HashSet<Edge>(edgesInCurrentRow));

    for (int i = endCalculateIndex - 1; i >= startIndex; i--) {
      Node prevNode = myGraph.getNode(i);
      oneUpStep(edgesInCurrentRow, currentNode, prevNode);

      if (i <= endIndex)
        result.add(new HashSet<Edge>(edgesInCurrentRow));

      currentNode = prevNode;
    }

    Collections.reverse(result);
    return result;
  }

  private static void oneUpStep(Set<Edge> edgesInCurrentRow, Node currentNode, Node prevNode) {
    edgesInCurrentRow.addAll(currentNode.getUpEdges());
    edgesInCurrentRow.removeAll(prevNode.getDownEdges());
  }
}
