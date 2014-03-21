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
package com.intellij.vcs.log.newgraph.gpaph.impl;

import com.intellij.util.SmartList;
import com.intellij.vcs.log.newgraph.PermanentGraphLayout;
import com.intellij.vcs.log.newgraph.SomeGraph;
import com.intellij.vcs.log.newgraph.gpaph.*;
import com.intellij.vcs.log.facade.utils.IntToIntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class MutableGraphWithHiddenNodes<T extends GraphWithElementsInfo> implements MutableGraph {

  @Nullable
  public static Edge containedCollapsedEdge(@NotNull GraphElement element) {
    if (element instanceof Edge) {
      Edge edge = (Edge)element;
      if (edge.getType() == Edge.Type.HIDE_FRAGMENT)
        return edge;

    } else {
      Node node = (Node)element;
      for (Edge upEdge : node.getUpEdges())
        if (upEdge.getType() == Edge.Type.HIDE_FRAGMENT) return upEdge;

      for (Edge downEdge : node.getDownEdges())
        if (downEdge.getType() == Edge.Type.HIDE_FRAGMENT) return downEdge;
    }

    return null;
  }

  @NotNull
  protected final IntToIntMap myVisibleToReal;

  @NotNull
  protected final T myGraph;

  @NotNull
  protected final PermanentGraphLayout myLayout;

  protected MutableGraphWithHiddenNodes(@NotNull IntToIntMap visibleToReal, @NotNull T graph, @NotNull PermanentGraphLayout layout) {
    myVisibleToReal = visibleToReal;
    myGraph = graph;
    myLayout = layout;
  }

  @Override
  public int getCountVisibleNodes() {
    return myVisibleToReal.shortSize();
  }

  @NotNull
  @Override
  public Node getNode(int visibleNodeIndex) {
    int nodeIndex = toNodeIndex(visibleNodeIndex);
    List<Edge> upEdges = convertToEdgeList(myGraph.getUpNodes(nodeIndex), nodeIndex, /*toUp = */ true);
    List<Edge> downEdges = convertToEdgeList(myGraph.getDownNodes(nodeIndex), nodeIndex, /*toUp = */ false);

    Node.Type nodeType = myGraph.getNodeType(nodeIndex);
    int nodeLayoutIndex = myLayout.getLayoutIndex(nodeIndex);
    return new NodeImpl(visibleNodeIndex, nodeType, upEdges, downEdges, nodeLayoutIndex);
  }

  @Override
  public int getIndexInPermanentGraph(int visibleNodeIndex) {
    return toNodeIndex(visibleNodeIndex);
  }

  public int toVisibleIndex(int nodeIndexInPermanentGraph) {
    if (nodeIndexInPermanentGraph == SomeGraph.NOT_LOAD_COMMIT)
      return SomeGraph.NOT_LOAD_COMMIT;
    return myVisibleToReal.getShortIndex(nodeIndexInPermanentGraph);
  }

  protected int toNodeIndex(int visibleNodeIndex) {
    if (visibleNodeIndex == SomeGraph.NOT_LOAD_COMMIT)
      return SomeGraph.NOT_LOAD_COMMIT;
    return myVisibleToReal.getLongIndex(visibleNodeIndex);
  }

  @NotNull
  private List<Edge> convertToEdgeList(@NotNull List<Integer> destinationNodeIndexes, int startNodeIndex, boolean toUp) {
    List<Edge> result = new SmartList<Edge>();
    for (int destinationIndex : destinationNodeIndexes) {
      int upNodeIndex, downNodeIndex;
      if (toUp) {
        upNodeIndex = destinationIndex;
        downNodeIndex = startNodeIndex;
      } else {
        downNodeIndex = destinationIndex;
        upNodeIndex = startNodeIndex;
      }
      Edge.Type type = myGraph.getEdgeType(upNodeIndex, downNodeIndex);
      int layoutIndex = getEdgeLayoutIndex(upNodeIndex, downNodeIndex);
      result.add(new EdgeImpl(toVisibleIndex(upNodeIndex), toVisibleIndex(downNodeIndex), type, layoutIndex));
    }
    return result;
  }

  private int getEdgeLayoutIndex(int upNodeIndex, int downNodeIndex) {
    if (downNodeIndex == SomeGraph.NOT_LOAD_COMMIT) {
      return myLayout.getLayoutIndex(upNodeIndex); // i.e. edge to not load commit
    }
    int upNodeLayoutIndex = myLayout.getLayoutIndex(upNodeIndex);
    int downNodeLayoutIndex = myLayout.getLayoutIndex(downNodeIndex);
    return Math.max(upNodeLayoutIndex, downNodeLayoutIndex);
  }
}
