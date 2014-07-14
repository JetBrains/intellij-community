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
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.graph.api.LinearGraphWithElementInfo;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.vcs.log.graph.parser.CommitParser.nextSeparatorIndex;
import static com.intellij.vcs.log.graph.parser.CommitParser.toLines;

public class LinearGraphWithElementsInfoParser {

  public static LinearGraphWithElementInfo parse(@NotNull String in) {
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

    GraphNode graphNode = new GraphNode(pair.first, parseGraphNodeType(pair.second));
    List<GraphEdge> edges = new ArrayList<GraphEdge>();

    for (String edge : line.substring(separatorIndex + 2).split("\\s")) {
      if (!edge.isEmpty()) {
        pair = parseNumberWithChar(edge);
        edges.add(new GraphEdge(graphNode.getNodeIndex(), pair.first, parseGraphEdgeType(pair.second)));
      }
    }
    return new Pair<GraphNode, List<GraphEdge>>(graphNode, edges);
  }

  public static GraphNode.Type parseGraphNodeType(char c) {
    if (c == 'U')
      return GraphNode.Type.USUAL;

    throw new IllegalStateException("Illegal char for graph node type: " + c);
  }

  public static GraphEdge.Type parseGraphEdgeType(char c) {
    if (c == 'U')
      return GraphEdge.Type.USUAL;
    if (c == 'H')
      return GraphEdge.Type.HIDE;

    throw new IllegalStateException("Illegal char for graph edge type: " + c);
  }

  private static Pair<Integer, Character> parseNumberWithChar(@NotNull String in) {
    return new Pair<Integer, Character>(Integer.decode(in.substring(0, in.length() - 2)), in.charAt(in.length() - 1));
  }

  private static class TestLinearGraphWithElementsInfo implements LinearGraphWithElementInfo {

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

    @NotNull
    @Override
    public GraphNode.Type getNodeType(int nodeIndex) {
      return myGraphNodes.get(nodeIndex).getType();
    }

    @NotNull
    @Override
    public GraphEdge.Type getEdgeType(int upNodeIndex, int downNodeIndex) {
      for (GraphEdge downEdge : myDownEdges.get(upNodeIndex)) {
        if (downEdge.getDownNodeIndex() == downNodeIndex)
          return downEdge.getType();
      }
      throw new IllegalStateException("Not found edge: " + upNodeIndex + ", " + downNodeIndex);
    }

    @Override
    public int nodesCount() {
      return myGraphNodes.size();
    }

    @NotNull
    @Override
    public List<Integer> getUpNodes(int nodeIndex) {
      List<Integer> result = new ArrayList<Integer>();
      for (GraphEdge upEdge : myUpEdges.get(nodeIndex)) {
        result.add(upEdge.getUpNodeIndex());
      }
      return result;
    }

    @NotNull
    @Override
    public List<Integer> getDownNodes(int nodeIndex) {
      List<Integer> result = new ArrayList<Integer>();
      for (GraphEdge downEdge : myDownEdges.get(nodeIndex)) {
        result.add(downEdge.getDownNodeIndex());
      }
      return result;
    }
  }

}
