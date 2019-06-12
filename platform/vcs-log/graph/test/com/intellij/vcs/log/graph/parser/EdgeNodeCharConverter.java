// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.parser;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphNodeType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class EdgeNodeCharConverter {
  private static final Map<Character, GraphNodeType> GRAPH_NODE_TYPE_MAP = new HashMap<>();
  private static final Map<GraphNodeType, Character> REVERSE_GRAPH_NODE_TYPE_MAP;

  private static final Map<Character, GraphEdgeType> GRAPH_EDGE_TYPE_MAP = new HashMap<>();
  private static final Map<GraphEdgeType, Character> REVERSE_GRAPH_EDGE_TYPE_MAP;

  static {
    GRAPH_NODE_TYPE_MAP.put('U', GraphNodeType.USUAL);
    GRAPH_NODE_TYPE_MAP.put('G', GraphNodeType.UNMATCHED);
    GRAPH_NODE_TYPE_MAP.put('N', GraphNodeType.NOT_LOAD_COMMIT);

    GRAPH_EDGE_TYPE_MAP.put('U', GraphEdgeType.USUAL);
    GRAPH_EDGE_TYPE_MAP.put('D', GraphEdgeType.DOTTED);
    GRAPH_EDGE_TYPE_MAP.put('N', GraphEdgeType.NOT_LOAD_COMMIT);
    GRAPH_EDGE_TYPE_MAP.put('P', GraphEdgeType.DOTTED_ARROW_UP);
    GRAPH_EDGE_TYPE_MAP.put('O', GraphEdgeType.DOTTED_ARROW_DOWN);

    REVERSE_GRAPH_NODE_TYPE_MAP = ContainerUtil.reverseMap(GRAPH_NODE_TYPE_MAP);
    REVERSE_GRAPH_EDGE_TYPE_MAP = ContainerUtil.reverseMap(GRAPH_EDGE_TYPE_MAP);
  }

  @NotNull
  public static GraphNodeType parseGraphNodeType(char type) {
    GraphNodeType nodeType = GRAPH_NODE_TYPE_MAP.get(type);
    if (nodeType == null) throw new IllegalStateException("Illegal char for graph node type: " + type);
    return nodeType;
  }

  @NotNull
  public static GraphEdgeType parseGraphEdgeType(char type) {
    GraphEdgeType nodeType = GRAPH_EDGE_TYPE_MAP.get(type);
    if (nodeType == null) throw new IllegalStateException("Illegal char for graph edge type: " + type);
    return nodeType;
  }

  public static char toChar(@NotNull GraphEdgeType type) {
    return REVERSE_GRAPH_EDGE_TYPE_MAP.get(type);
  }

  public static char toChar(@NotNull GraphNodeType type) {
    return REVERSE_GRAPH_NODE_TYPE_MAP.get(type);
  }

}
