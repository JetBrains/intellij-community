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
package com.intellij.vcs.log.graph.api;

import com.intellij.util.SmartList;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class RefactoringLinearGraph implements LinearGraph {

  @NotNull
  @Override
  public List<GraphEdge> getAdjacentEdges(int nodeIndex) {
    List<GraphEdge> result = new SmartList<GraphEdge>();
    for (int node : getDownNodes(nodeIndex)) {
      if (node == LinearGraph.NOT_LOAD_COMMIT) {
        result.add(new GraphEdge(nodeIndex, null, GraphEdgeType.NOT_LOAD_COMMIT));
      } else {
        result.add(new GraphEdge(nodeIndex, node, GraphEdgeType.USUAL));
      }
    }
    for (int node : getUpNodes(nodeIndex)) {
      assert node != LinearGraph.NOT_LOAD_COMMIT;
      result.add(new GraphEdge(node, nodeIndex, GraphEdgeType.USUAL));
    }
    return result;
  }

  @NotNull
  @Override
  public GraphNode getGraphNode(int nodeIndex) {
    return new GraphNode(nodeIndex);
  }

  @Override
  public int getNodeIndexById(int nodeId) {
    return -1;
  }
}
