/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.graph.impl;

import com.intellij.util.graph.Graph;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ShortestPathFinder<Node> {
  private final Graph<Node> myGraph;

  public ShortestPathFinder(Graph<Node> graph) {
    myGraph = graph;
  }


  @Nullable
  public List<Node> findPath(Node start, Node finish) {
    Map<Node, Node> nextNodes = new HashMap<>();
    Deque<Node> queue = new ArrayDeque<>();
    queue.addLast(finish);

    boolean found = false;
    while (!queue.isEmpty()) {
      final Node node = queue.removeFirst();
      if (node.equals(start)) {
        found = true;
        break;
      }

      final Iterator<Node> in = myGraph.getIn(node);
      while (in.hasNext()) {
        Node prev = in.next();
        if (!nextNodes.containsKey(prev)) {
          nextNodes.put(prev, node);
          queue.addLast(prev);
        }
      }
    }

    if (!found) {
      return null;
    }
    List<Node> path = new ArrayList<>();
    Node current = start;
    while (!current.equals(finish)) {
      path.add(current);
      current = nextNodes.get(current);
    }
    path.add(finish);
    return path;
  }
}
