// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.vcs.log.graph.parser;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.vcs.log.graph.parser.CommitParser.nextSeparatorIndex;
import static com.intellij.vcs.log.graph.parser.CommitParser.toLines;
import static com.intellij.vcs.log.graph.parser.EdgeNodeCharConverter.parseGraphEdgeType;
import static com.intellij.vcs.log.graph.parser.EdgeNodeCharConverter.parseGraphNodeType;

public class LinearGraphParser {

  public static LinearGraph parse(@NotNull String in) {
    List<GraphNode> graphNodes = new ArrayList<>();

    Map<GraphNode, List<String>> edges = new HashMap<>();
    Int2IntMap nodeIdToNodeIndex = new Int2IntOpenHashMap();

    for (String line : toLines(in)) { // parse input and create nodes
      ParsedLine graphNodePair = parseLine(line, graphNodes.size());

      edges.put(graphNodePair.graphNode, graphNodePair.normalEdges);
      nodeIdToNodeIndex.put(graphNodePair.nodeId, graphNodes.size());
      graphNodes.add(graphNodePair.graphNode);
    }

    MultiMap<Integer, GraphEdge> upEdges = MultiMap.create();
    MultiMap<Integer, GraphEdge> downEdges = MultiMap.create();
    for (GraphNode graphNode : graphNodes) { // create edges
      for (String strEdge : edges.get(graphNode)) {
        NumberWithChar pairEdge = parseNumberWithChar(strEdge);
        GraphEdgeType type = parseGraphEdgeType(pairEdge.character);

        GraphEdge edge;
        switch (type) {
          case USUAL, DOTTED -> {
            assert nodeIdToNodeIndex.containsKey(pairEdge.nodeId);
            int downNodeIndex = nodeIdToNodeIndex.get(pairEdge.nodeId);
            edge = GraphEdge.createNormalEdge(graphNode.getNodeIndex(), downNodeIndex, type);
          }
          case NOT_LOAD_COMMIT, DOTTED_ARROW_DOWN, DOTTED_ARROW_UP ->
            edge = GraphEdge.createEdgeWithTargetId(graphNode.getNodeIndex(), pairEdge.nodeId, type);
          default -> throw new IllegalStateException("Unknown type: " + type);
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
  public static ParsedLine parseLine(@NotNull String line, int lineNumber) {
    int separatorIndex = nextSeparatorIndex(line, 0);
    NumberWithChar pair = parseNumberWithChar(line.substring(0, separatorIndex));

    GraphNode graphNode = new GraphNode(lineNumber, parseGraphNodeType(pair.character));

    String[] edges = line.substring(separatorIndex + 2).split("\\s");
    List<String> normalEdges = ContainerUtil.mapNotNull(edges, s -> {
      if (s.isEmpty()) return null;
      return s;
    });
    return new ParsedLine(pair.nodeId, graphNode, normalEdges);
  }

  private static NumberWithChar parseNumberWithChar(@NotNull String in) {
    return new NumberWithChar(Integer.decode(in.substring(0, in.length() - 2)), in.charAt(in.length() - 1));
  }

  private static class NumberWithChar {
    public final int nodeId;
    public final char character;

    private NumberWithChar(int nodeId, char character) {
      this.nodeId = nodeId;
      this.character = character;
    }
  }

  private static class ParsedLine {
    public final int nodeId;
    public final GraphNode graphNode;
    public final List<String> normalEdges;

    private ParsedLine(int nodeId, GraphNode graphNode, List<String> normalEdges) {
      this.nodeId = nodeId;
      this.graphNode = graphNode;
      this.normalEdges = normalEdges;
    }
  }

  private static final class TestLinearGraphWithElementsInfo implements LinearGraph {

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
      List<GraphEdge> result = new ArrayList<>();

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
