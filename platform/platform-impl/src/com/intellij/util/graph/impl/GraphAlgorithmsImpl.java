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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphAlgorithms;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class GraphAlgorithmsImpl extends GraphAlgorithms {
  @Override
  public <Node> List<Node> findShortestPath(@NotNull Graph<Node> graph, @NotNull Node start, @NotNull Node finish) {
    return new ShortestPathFinder<Node>(graph).findPath(start, finish);
  }

  @NotNull
  @Override
  public <Node> List<List<Node>> findKShortestPaths(@NotNull Graph<Node> graph, @NotNull Node start, @NotNull Node finish, int k,
                                                    @NotNull ProgressIndicator progressIndicator) {
    return new KShortestPathsFinder<Node>(graph, start, finish, progressIndicator).findShortestPaths(k);
  }

  @NotNull
  @Override
  public <Node> Set<List<Node>> findCycles(@NotNull Graph<Node> graph, @NotNull Node node) {
    return new CycleFinder<Node>(graph).getNodeCycles(node);
  }

  @NotNull
  @Override
  public <Node> Graph<Node> invertEdgeDirections(@NotNull final Graph<Node> graph) {
    return new Graph<Node>() {
      public Collection<Node> getNodes() {
        return graph.getNodes();
      }

      public Iterator<Node> getIn(final Node n) {
        return graph.getOut(n);
      }

      public Iterator<Node> getOut(final Node n) {
        return graph.getIn(n);
      }

    };
  }

  @NotNull
  @Override
  public <Node> List<List<Node>> removePathsWithCycles(@NotNull List<List<Node>> paths) {
    final List<List<Node>> result = new ArrayList<List<Node>>();
    for (List<Node> path : paths) {
      if (!containsCycle(path)) {
        result.add(path);
      }
    }
    return result;
  }

  private static boolean containsCycle(List<?> path) {
    return new HashSet<Object>(path).size() != path.size();
  }
}
