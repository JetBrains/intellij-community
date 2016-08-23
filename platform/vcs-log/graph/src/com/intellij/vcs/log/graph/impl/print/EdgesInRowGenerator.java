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
package com.intellij.vcs.log.graph.impl.print;

import com.intellij.util.containers.SLRUMap;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EdgesInRowGenerator {
  private static final int CACHE_SIZE = 10;
  private static final int BLOCK_SIZE = 40;

  private final int WALK_SIZE;

  @NotNull private final LinearGraph myGraph;

  @NotNull private final SLRUMap<Integer, GraphEdges> cacheNU = new SLRUMap<>(CACHE_SIZE, CACHE_SIZE * 2);
  private final SLRUMap<Integer, GraphEdges> cacheND = new SLRUMap<>(CACHE_SIZE, CACHE_SIZE * 2);

  public EdgesInRowGenerator(@NotNull LinearGraph graph) {
    this(graph, 1000);
  }

  public EdgesInRowGenerator(@NotNull LinearGraph graph, int walk_size) {
    myGraph = graph;
    WALK_SIZE = walk_size;
  }

  @NotNull
  public Set<GraphEdge> getEdgesInRow(int rowIndex) {
    GraphEdges neighborU = getNeighborU(rowIndex);
    while (neighborU.myRow < rowIndex) {
      neighborU = oneDownStep(neighborU);
    }

    GraphEdges neighborD = getNeighborD(rowIndex);
    while (neighborD.myRow > rowIndex) {
      neighborD = oneUpStep(neighborD);
    }

    Set<GraphEdge> result = neighborU.myEdges;
    result.addAll(neighborD.myEdges);
    return result;
  }

  public void invalidate() {
    cacheNU.clear();
    cacheND.clear();
  }

  @NotNull
  private GraphEdges getNeighborU(int rowIndex) {
    int upNeighborIndex = getUpNeighborIndex(rowIndex);
    GraphEdges graphEdges = cacheNU.get(upNeighborIndex);
    if (graphEdges == null) {
      graphEdges = getUCorrectEdges(upNeighborIndex);
      cacheNU.put(upNeighborIndex, graphEdges);
    }
    return graphEdges.copyInstance();
  }

  @NotNull
  private GraphEdges getNeighborD(int rowIndex) {
    int downNeighborIndex = getUpNeighborIndex(rowIndex) + BLOCK_SIZE;

    if (downNeighborIndex >= myGraph.nodesCount()) {
      return new GraphEdges(myGraph.nodesCount() - 1);
    }

    GraphEdges graphEdges = cacheND.get(downNeighborIndex);
    if (graphEdges == null) {
      graphEdges = getDCorrectEdges(downNeighborIndex);
      cacheND.put(downNeighborIndex, graphEdges);
    }
    return graphEdges.copyInstance();
  }

  private static int getUpNeighborIndex(int rowIndex) {
    return (rowIndex / BLOCK_SIZE) * BLOCK_SIZE;
  }

  @NotNull
  private GraphEdges getUCorrectEdges(int rowIndex) {
    int startCalculateIndex = Math.max(rowIndex - WALK_SIZE, 0);
    GraphEdges graphEdges = new GraphEdges(startCalculateIndex);

    for (int i = startCalculateIndex; i < rowIndex; i++) {
      graphEdges = oneDownStep(graphEdges);
    }
    return graphEdges;
  }

  @NotNull
  private GraphEdges getDCorrectEdges(int rowIndex) {
    int endCalculateIndex = Math.min(rowIndex + WALK_SIZE, myGraph.nodesCount() - 1);
    GraphEdges graphEdges = new GraphEdges(endCalculateIndex);

    for (int i = endCalculateIndex; i > rowIndex; i--) {
      graphEdges = oneUpStep(graphEdges);
    }
    return graphEdges;
  }

  @NotNull
  private GraphEdges oneDownStep(@NotNull GraphEdges graphEdges) {
    Set<GraphEdge> edgesInCurrentRow = graphEdges.myEdges;
    int currentRow = graphEdges.myRow;

    edgesInCurrentRow.addAll(createDownEdges(currentRow));
    edgesInCurrentRow.removeAll(createUpEdges(currentRow + 1));

    return new GraphEdges(edgesInCurrentRow, currentRow + 1);
  }

  @NotNull
  private GraphEdges oneUpStep(@NotNull GraphEdges graphEdges) {
    Set<GraphEdge> edgesInCurrentRow = graphEdges.myEdges;
    int currentRow = graphEdges.myRow;

    edgesInCurrentRow.addAll(createUpEdges(currentRow));
    edgesInCurrentRow.removeAll(createDownEdges(currentRow - 1));
    return new GraphEdges(edgesInCurrentRow, currentRow - 1);
  }

  public List<GraphEdge> createUpEdges(int nodeIndex) {
    return myGraph.getAdjacentEdges(nodeIndex, EdgeFilter.NORMAL_UP);
  }

  public List<GraphEdge> createDownEdges(int nodeIndex) {
    return myGraph.getAdjacentEdges(nodeIndex, EdgeFilter.NORMAL_DOWN);
  }

  private static class GraphEdges {
    // this must be mutably set
    @NotNull private final Set<GraphEdge> myEdges;
    private final int myRow;

    private GraphEdges(int row) {
      this(new HashSet<>(), row);
    }

    private GraphEdges(@NotNull Set<GraphEdge> edges, int row) {
      myEdges = edges;
      myRow = row;
    }

    @NotNull
    GraphEdges copyInstance() {
      return new GraphEdges(new HashSet<>(myEdges), myRow);
    }
  }
}
