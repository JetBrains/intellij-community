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
import com.intellij.util.Chunk;
import com.intellij.util.graph.*;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class GraphAlgorithmsImpl extends GraphAlgorithms {
  @Override
  public <Node> List<Node> findShortestPath(@NotNull Graph<Node> graph, @NotNull Node start, @NotNull Node finish) {
    return new ShortestPathFinder<>(graph).findPath(start, finish);
  }

  @NotNull
  @Override
  public <Node> List<List<Node>> findKShortestPaths(@NotNull Graph<Node> graph, @NotNull Node start, @NotNull Node finish, int k,
                                                    @NotNull ProgressIndicator progressIndicator) {
    return new KShortestPathsFinder<>(graph, start, finish, progressIndicator).findShortestPaths(k);
  }

  @NotNull
  @Override
  public <Node> Set<List<Node>> findCycles(@NotNull Graph<Node> graph, @NotNull Node node) {
    return new CycleFinder<>(graph).getNodeCycles(node);
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
  public <Node> Graph<Chunk<Node>> computeSCCGraph(@NotNull final Graph<Node> graph) {
    final DFSTBuilder<Node> builder = new DFSTBuilder<>(graph);
    final TIntArrayList sccs = builder.getSCCs();

    final List<Chunk<Node>> chunks = new ArrayList<>(sccs.size());
    final Map<Node, Chunk<Node>> nodeToChunkMap = new LinkedHashMap<>();
    sccs.forEach(new TIntProcedure() {
      int myTNumber = 0;
      public boolean execute(int size) {
        final Set<Node> chunkNodes = new LinkedHashSet<>();
        final Chunk<Node> chunk = new Chunk<>(chunkNodes);
        chunks.add(chunk);
        for (int j = 0; j < size; j++) {
          final Node node = builder.getNodeByTNumber(myTNumber + j);
          chunkNodes.add(node);
          nodeToChunkMap.put(node, chunk);
        }

        myTNumber += size;
        return true;
      }
    });

    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<Chunk<Node>>() {
      public Collection<Chunk<Node>> getNodes() {
        return chunks;
      }

      public Iterator<Chunk<Node>> getIn(Chunk<Node> chunk) {
        final Set<Node> chunkNodes = chunk.getNodes();
        final Set<Chunk<Node>> ins = new LinkedHashSet<>();
        for (final Node node : chunkNodes) {
          for (Iterator<Node> nodeIns = graph.getIn(node); nodeIns.hasNext(); ) {
            final Node in = nodeIns.next();
            if (!chunk.containsNode(in)) {
              ins.add(nodeToChunkMap.get(in));
            }
          }
        }
        return ins.iterator();
      }
    }));
  }

  @Override
  public <Node> void collectOutsRecursively(@NotNull Graph<Node> graph, Node start, Set<Node> set) {
    if (!set.add(start)) {
      return;
    }
    Iterator<Node> iterator = graph.getOut(start);
    while (iterator.hasNext()) {
      Node node = iterator.next();
      collectOutsRecursively(graph, node, set);
    }
  }

  @NotNull
  @Override
  public <Node> Collection<Chunk<Node>> computeStronglyConnectedComponents(@NotNull Graph<Node> graph) {
    return computeSCCGraph(graph).getNodes();
  }

  @NotNull
  @Override
  public <Node> List<List<Node>> removePathsWithCycles(@NotNull List<List<Node>> paths) {
    final List<List<Node>> result = new ArrayList<>();
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
