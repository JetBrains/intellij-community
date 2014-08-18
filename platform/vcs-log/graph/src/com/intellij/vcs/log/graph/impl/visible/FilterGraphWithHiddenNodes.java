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

package com.intellij.vcs.log.graph.impl.visible;

import com.intellij.openapi.util.Condition;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LinearGraphWithHiddenNodes;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.ListenerController;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import com.intellij.vcs.log.graph.utils.impl.SetListenerController;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FilterGraphWithHiddenNodes implements LinearGraphWithHiddenNodes {
  @NotNull
  private final LinearGraphWithHiddenNodes myDelegateGraph;

  @NotNull
  private final Flags myVisibleNodes;

  @NotNull
  private final DottedEdges myDottedEdges;

  @NotNull
  private final SetListenerController<UpdateListener> myListenerController = new SetListenerController<UpdateListener>();

  public FilterGraphWithHiddenNodes(@NotNull LinearGraphWithHiddenNodes delegateGraph, @NotNull Condition<Integer> isVisibleNode) {
    myDelegateGraph = delegateGraph;
    myVisibleNodes = new BitSetFlags(delegateGraph.nodesCount());
    for (int i = 0; i < delegateGraph.nodesCount(); i++) {
      myVisibleNodes.set(i, delegateGraph.nodeIsVisible(i) && isVisibleNode.value(i)); // todo: think about it: may be drop myVisibleNodes
    }

    MultiMap<Integer, Integer> edges = DottedEdgesComputer.compute(myDelegateGraph, myVisibleNodes);
    myDottedEdges = DottedEdges.newInstance(edges);

    addUpdateListener();
  }

  private void addUpdateListener() {
    myDelegateGraph.getListenerController().addListener(new UpdateListener() {
      @Override
      public void update(final int upNodeIndex, final int downNodeIndex) {
        myListenerController.callListeners(new Consumer<UpdateListener>() {
          @Override
          public void consume(UpdateListener updateListener) {
            updateListener.update(upNodeIndex, downNodeIndex);
          }
        });
      }
    });
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
    if (myDottedEdges.getAdjacentNodes(upNodeIndex).contains(downNodeIndex))
      return GraphEdge.Type.HIDE;
    else
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
    List<Integer> upNodes = new SmartList<Integer>();
    for (int upNode : myDelegateGraph.getUpNodes(nodeIndex)) {
      if (nodeIsVisible(upNode))
        upNodes.add(upNode);
    }
    for (int adjNode : myDottedEdges.getAdjacentNodes(nodeIndex)) {
      if (adjNode < nodeIndex)
        upNodes.add(adjNode);
    }
    return upNodes;
  }

  @NotNull
  @Override
  public List<Integer> getDownNodes(int nodeIndex) {
    List<Integer> downNodes = new SmartList<Integer>();
    for (int downNode : myDelegateGraph.getDownNodes(nodeIndex)) {
      if (downNode != LinearGraph.NOT_LOAD_COMMIT && nodeIsVisible(downNode))
        downNodes.add(downNode);
    }
    for (int adjNode : myDottedEdges.getAdjacentNodes(nodeIndex)) {
      if (adjNode > nodeIndex)
        downNodes.add(adjNode);
    }
    return downNodes;
  }
}
