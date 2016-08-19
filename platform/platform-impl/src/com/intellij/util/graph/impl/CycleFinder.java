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

package com.intellij.util.graph.impl;

import com.intellij.util.graph.Graph;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 * Date: Feb 13, 2005
 */
public class CycleFinder<Node> {
  private final Graph<Node> myGraph;

  public CycleFinder(Graph<Node> graph) {
    myGraph = graph;
  }

  @NotNull
  public Set<List<Node>> getNodeCycles(final Node node){
    final Set<List<Node>> result = new HashSet<>();


    final Graph<Node> graphWithoutNode = new Graph<Node>() {
      public Collection<Node> getNodes() {
        final Collection<Node> nodes = myGraph.getNodes();
        nodes.remove(node);
        return nodes;
      }

      public Iterator<Node> getIn(final Node n) {
        final HashSet<Node> nodes = new HashSet<>();
        final Iterator<Node> in = myGraph.getIn(n);
        while (in.hasNext()) {
          nodes.add(in.next());
        }
        nodes.remove(node);
        return nodes.iterator();
      }

      public Iterator<Node> getOut(final Node n) {
        final HashSet<Node> nodes = new HashSet<>();
        final Iterator<Node> out = myGraph.getOut(n);
        while (out.hasNext()) {
          nodes.add(out.next());
        }
        nodes.remove(node);
        return nodes.iterator();
      }

    };

    final HashSet<Node> inNodes = new HashSet<>();
    final Iterator<Node> in = myGraph.getIn(node);
    while (in.hasNext()) {
      inNodes.add(in.next());
    }
    final HashSet<Node> outNodes = new HashSet<>();
    final Iterator<Node> out = myGraph.getOut(node);
    while (out.hasNext()) {
      outNodes.add(out.next());
    }

    final HashSet<Node> retainNodes = new HashSet<>(inNodes);
    retainNodes.retainAll(outNodes);
    for (Node node1 : retainNodes) {
      ArrayList<Node> oneNodeCycle = new ArrayList<>();
      oneNodeCycle.add(node1);
      oneNodeCycle.add(node);
      result.add(oneNodeCycle);
    }

    inNodes.removeAll(retainNodes);
    outNodes.removeAll(retainNodes);

    ShortestPathFinder<Node> finder = new ShortestPathFinder<>(graphWithoutNode);
    for (Node fromNode : outNodes) {
      for (Node toNode : inNodes) {
        final List<Node> shortestPath = finder.findPath(fromNode, toNode);
        if (shortestPath != null) {
          ArrayList<Node> path = new ArrayList<>();
          path.addAll(shortestPath);
          path.add(node);
          result.add(path);
        }
      }
    }
    return result;
  }
}
