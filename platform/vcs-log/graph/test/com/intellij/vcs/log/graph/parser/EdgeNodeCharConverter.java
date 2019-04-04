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

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphNodeType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class EdgeNodeCharConverter {
  private static final Map<Character, GraphNodeType> GRAPH_NODE_TYPE_MAP = ContainerUtil.newHashMap();
  private static final Map<GraphNodeType, Character> REVERSE_GRAPH_NODE_TYPE_MAP;

  private static final Map<Character, GraphEdgeType> GRAPH_EDGE_TYPE_MAP = ContainerUtil.newHashMap();
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
