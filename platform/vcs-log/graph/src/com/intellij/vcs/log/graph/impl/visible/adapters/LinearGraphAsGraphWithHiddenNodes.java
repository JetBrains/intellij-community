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

package com.intellij.vcs.log.graph.impl.visible.adapters;

import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LinearGraphWithHiddenNodes;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.IdFlags;
import com.intellij.vcs.log.graph.utils.ListenerController;
import com.intellij.vcs.log.graph.utils.impl.SetListenerController;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Methods getUpNodes or getDownNodes may return invisible nodes.
 * It is done for performance,
 */

public class LinearGraphAsGraphWithHiddenNodes implements LinearGraphWithHiddenNodes {

  @NotNull
  private final LinearGraph myDelegateGraph;
  @NotNull
  private final Flags myVisibleNodes;

  @NotNull
  private final SetListenerController<UpdateListener> myListenerController = new SetListenerController<UpdateListener>();

  public LinearGraphAsGraphWithHiddenNodes(@NotNull LinearGraph delegateGraph) {
    this(delegateGraph, new IdFlags(delegateGraph.nodesCount(), true));
  }

  public LinearGraphAsGraphWithHiddenNodes(@NotNull LinearGraph delegateGraph, @NotNull Flags visibleNodes) {
    myDelegateGraph = delegateGraph;
    myVisibleNodes = visibleNodes;
  }

  @Override
  public boolean nodeIsVisible(int nodeIndex) {
    return myVisibleNodes.get(nodeIndex);
  }

  @NotNull
  @Override
  public GraphNode.Type getNodeType(int nodeIndex) {
    return GraphNode.Type.USUAL;
  }

  @NotNull
  @Override
  public GraphEdge.Type getEdgeType(int upNodeIndex, int downNodeIndex) {
    return GraphEdge.Type.USUAL;
  }

  @NotNull
  @Override
  public ListenerController<UpdateListener> getListenerController() {
    return myListenerController;
  }

  @Override
  public int nodesCount() {
    return myDelegateGraph.nodesCount();
  }

  @NotNull
  @Override
  public List<Integer> getUpNodes(int nodeIndex) {
    return myDelegateGraph.getUpNodes(nodeIndex);
  }

  @NotNull
  @Override
  public List<Integer> getDownNodes(int nodeIndex) {
    return myDelegateGraph.getDownNodes(nodeIndex);
  }
}
