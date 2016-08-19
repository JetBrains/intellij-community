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

package com.intellij.vcs.log.graph.parser;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.vcs.log.graph.parser.CommitParser.nextSeparatorIndex;
import static com.intellij.vcs.log.graph.parser.CommitParser.toLines;
import static com.intellij.vcs.log.graph.parser.EdgeNodeCharConverter.parseGraphEdgeType;
import static com.intellij.vcs.log.graph.parser.EdgeNodeCharConverter.parseGraphNodeType;

public class LinearGraphParser {

  public static LinearGraph parse(@NotNull String in) {
    List<GraphNode> graphNodes = new ArrayList<>();

    Map<GraphNode, List<String>> edges = ContainerUtil.newHashMap();
    Map<Integer, Integer> nodeIdToNodeIndex = ContainerUtil.newHashMap();

    for (String line : toLines(in)) { // parse input and create nodes
      Pair<Pair<Integer, GraphNode>, List<String>> graphNodePair = parseLine(line, graphNodes.size());
      GraphNode graphNode = graphNodePair.first.second;

      edges.put(graphNode, graphNodePair.second);
      nodeIdToNodeIndex.put(graphNodePair.first.first, graphNodes.size());
      graphNodes.add(graphNode);
    }

    MultiMap<Integer, GraphEdge> upEdges = MultiMap.create();
    MultiMap<Integer, GraphEdge> downEdges = MultiMap.create();
    for (GraphNode graphNode : graphNodes) { // create edges
      for (String strEdge : edges.get(graphNode)) {
        Pair<Integer, Character> pairEdge = parseNumberWithChar(strEdge);
        GraphEdgeType type = parseGraphEdgeType(pairEdge.second);

        GraphEdge edge;
        switch (type) {
          case USUAL:
          case DOTTED:
            Integer downNodeIndex = nodeIdToNodeIndex.get(pairEdge.first);
            assert downNodeIndex != null;
            edge = GraphEdge.createNormalEdge(graphNode.getNodeIndex(), downNodeIndex, type);
            break;

          case NOT_LOAD_COMMIT:
          case DOTTED_ARROW_DOWN:
          case DOTTED_ARROW_UP:
            edge = GraphEdge.createEdgeWithTargetId(graphNode.getNodeIndex(), pairEdge.first, type);
            break;

          default:
            throw new IllegalStateException("Unknown type: " + type);
        }
        if (edge.getUpNodeIndex() != null) downEdges.putValue(edge.getUpNodeIndex(), edge);
        if (edge.getDownNodeIndex() != null) upEdges.putValue(edge.getDownNodeIndex(), edge);
      }
    }

    return new TestLinearGraphWithElementsInfo(graphNodes, upEdges, downEdges);
  }

  /**
   * Example input line:
   * 0_U|-1_U 2_D
   */
  public static Pair<Pair<Integer, GraphNode>, List<String>> parseLine(@NotNull String line, int lineNumber) {
    int separatorIndex = nextSeparatorIndex(line, 0);
    Pair<Integer, Character> pair = parseNumberWithChar(line.substring(0, separatorIndex));

    GraphNode graphNode = new GraphNode(lineNumber, parseGraphNodeType(pair.second));

    String[] edges = line.substring(separatorIndex + 2).split("\\s");
    List<String> normalEdges = ContainerUtil.mapNotNull(edges, new Function<String, String>() {
      @Nullable
      @Override
      public String fun(String s) {
        if (s.isEmpty()) return null;
        return s;
      }
    });
    return Pair.create(Pair.create(pair.first, graphNode), normalEdges);
  }

  private static Pair<Integer, Character> parseNumberWithChar(@NotNull String in) {
    return new Pair<>(Integer.decode(in.substring(0, in.length() - 2)), in.charAt(in.length() - 1));
  }

  private static class TestLinearGraphWithElementsInfo implements LinearGraph {

    private final List<GraphNode> myGraphNodes;
    private final MultiMap<Integer, GraphEdge> myUpEdges;
    private final MultiMap<Integer, GraphEdge> myDownEdges;

    private TestLinearGraphWithElementsInfo(List<GraphNode> graphNodes,
                                            MultiMap<Integer, GraphEdge> upEdges,
                                            MultiMap<Integer, GraphEdge> downEdges) {
      myGraphNodes = graphNodes;
      myUpEdges = upEdges;
      myDownEdges = downEdges;
    }

    @Override
    public int nodesCount() {
      return myGraphNodes.size();
    }

    @NotNull
    @Override
    public List<GraphEdge> getAdjacentEdges(int nodeIndex, @NotNull EdgeFilter filter) {
      List<GraphEdge> result = ContainerUtil.newArrayList();

      for (GraphEdge upEdge : myUpEdges.get(nodeIndex)) {
        if (upEdge.getType().isNormalEdge() && filter.upNormal) result.add(upEdge);
        if (!upEdge.getType().isNormalEdge() && filter.special) result.add(upEdge);
      }

      for (GraphEdge downEdge : myDownEdges.get(nodeIndex)) {
        if (downEdge.getType().isNormalEdge() && filter.downNormal) result.add(downEdge);
        if (!downEdge.getType().isNormalEdge() && filter.special) result.add(downEdge);
      }

      return result;
    }

    @NotNull
    @Override
    public GraphNode getGraphNode(int nodeIndex) {
      return myGraphNodes.get(nodeIndex);
    }

    @Override
    public int getNodeId(int nodeIndex) {
      assert nodeIndex > 0 && nodeIndex < nodesCount() : "Bad nodeIndex: " + nodeIndex;
      return nodeIndex;
    }

    @Override
    @Nullable
    public Integer getNodeIndex(int nodeId) {
      if (nodeId >= 0 && nodeId < nodesCount()) return nodeId;
      return null;
    }
  }
}
