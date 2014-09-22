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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.elements.GraphNodeType;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.vcs.log.graph.parser.CommitParser.nextSeparatorIndex;
import static com.intellij.vcs.log.graph.parser.CommitParser.toLines;

public class LinearGraphParser {

  public static LinearGraph parse(@NotNull String in) {
    List<GraphNode> graphNodes = new ArrayList<GraphNode>();
    MultiMap<Integer, GraphEdge> upEdges = MultiMap.create();
    MultiMap<Integer, GraphEdge> downEdges = MultiMap.create();

    for (String line : toLines(in)) {
      Pair<GraphNode, List<GraphEdge>> pair = parseLine(line);
      assert pair.first.getNodeIndex() == graphNodes.size();
      graphNodes.add(pair.first);

      for (GraphEdge graphEdge : pair.second) {
        upEdges.putValue(graphEdge.getDownNodeIndex(), graphEdge);
        downEdges.putValue(graphEdge.getUpNodeIndex(), graphEdge);
      }
    }
    return new TestLinearGraphWithElementsInfo(graphNodes, upEdges, downEdges);
  }

  /**
   * Example input line:
   * 0_U|-1_U 2_H
   */
  public static Pair<GraphNode, List<GraphEdge>> parseLine(@NotNull String line) {
    int separatorIndex = nextSeparatorIndex(line, 0);
    Pair<Integer, Character> pair = parseNumberWithChar(line.substring(0, separatorIndex));

    GraphNode graphNode = new GraphNode(pair.first,pair.first, parseGraphNodeType(pair.second));
    List<GraphEdge> edges = new ArrayList<GraphEdge>();

    for (String edge : line.substring(separatorIndex + 2).split("\\s")) {
      if (!edge.isEmpty()) {
        pair = parseNumberWithChar(edge);
        edges.add(new GraphEdge(graphNode.getNodeIndex(), pair.first, parseGraphEdgeType(pair.second)));
      }
    }
    return Pair.create(graphNode, edges);
  }

  public static GraphNodeType parseGraphNodeType(char c) {
    switch (c) {
      case 'U': return GraphNodeType.USUAL;
      case 'G': return GraphNodeType.GRAY;
      case 'N': return GraphNodeType.NOT_LOAD_COMMIT;
      default: throw new IllegalStateException("Illegal char for graph node type: " + c);
    }
  }

  public static GraphEdgeType parseGraphEdgeType(char c) {
    switch (c) {
      case 'U':
        return GraphEdgeType.USUAL;
      case 'H':
      case 'D':
        return GraphEdgeType.DOTTED;
      case 'N':
        return GraphEdgeType.NOT_LOAD_COMMIT;
      default:
        throw new IllegalStateException("Illegal char for graph edge type: " + c);
    }
  }

  private static Pair<Integer, Character> parseNumberWithChar(@NotNull String in) {
    return new Pair<Integer, Character>(Integer.decode(in.substring(0, in.length() - 2)), in.charAt(in.length() - 1));
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
    public List<Integer> getUpNodes(int nodeIndex) {
      return LinearGraphUtils.getUpNodes(this, nodeIndex);
    }

    @NotNull
    @Override
    public List<Integer> getDownNodes(int nodeIndex) {
      return LinearGraphUtils.getDownNodes(this, nodeIndex);
    }

    @NotNull
    @Override
    public List<GraphEdge> getAdjacentEdges(int nodeIndex) {
      return ContainerUtil.newArrayList(ContainerUtil.concat(myUpEdges.get(nodeIndex), myDownEdges.get(nodeIndex)));
    }

    @NotNull
    @Override
    public GraphNode getGraphNode(int nodeIndex) {
      return myGraphNodes.get(nodeIndex);
    }

    @Override
    @Nullable
    public Integer getNodeIndexById(int nodeId) {
      if (nodeId >= 0 && nodeId < nodesCount())
        return nodeId;
      return null;
    }
  }
}
