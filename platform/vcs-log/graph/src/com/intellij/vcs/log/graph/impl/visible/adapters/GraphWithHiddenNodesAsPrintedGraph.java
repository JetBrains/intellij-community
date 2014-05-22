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

import com.intellij.util.BooleanFunction;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LinearGraphWithHiddenNodes;
import com.intellij.vcs.log.graph.api.PrintedLinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.IntToIntMap;
import com.intellij.vcs.log.graph.utils.UpdatableIntToIntMap;
import com.intellij.vcs.log.graph.utils.impl.ListIntToIntMap;
import com.intellij.vcs.log.graph.api.GraphLayout;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.List;

public class GraphWithHiddenNodesAsPrintedGraph implements PrintedLinearGraph {

  @NotNull
  protected final GraphLayout myPermanentGraphLayout;

  @NotNull
  protected final LinearGraphWithHiddenNodes myDelegateGraph;

  @NotNull
  protected final UpdatableIntToIntMap myIntToIntMap;

  public GraphWithHiddenNodesAsPrintedGraph(@NotNull final LinearGraphWithHiddenNodes delegateGraph,
                                            @NotNull GraphLayout permanentGraphLayout) {
    myPermanentGraphLayout = permanentGraphLayout;
    myDelegateGraph = delegateGraph;
    myIntToIntMap = ListIntToIntMap.newInstance(new BooleanFunction<Integer>() {
      @Override
      public boolean fun(Integer integer) {
        return delegateGraph.nodeIsVisible(integer);
      }
    }, delegateGraph.nodesCount());

    myDelegateGraph.getListenerController().addListener(new LinearGraphWithHiddenNodes.UpdateListener() {
      @Override
      public void update(int upNodeIndex, int downNodeIndex) {
        myIntToIntMap.update(upNodeIndex, downNodeIndex);
      }
    });
  }

  @Override
  public int getLayoutIndex(int nodeIndex) {
    return myPermanentGraphLayout.getLayoutIndex(getIndexInPermanentGraph(nodeIndex));
  }

  @Override
  public int getHeadLayoutIndex(int nodeIndex) {
    int headNodeIndex = myPermanentGraphLayout.getOneOfHeadNodeIndex(getIndexInPermanentGraph(nodeIndex));
    return myPermanentGraphLayout.getLayoutIndex(headNodeIndex);
  }

  @NotNull
  @Override
  public GraphNode.Type getNodeType(int nodeIndex) {
    return myDelegateGraph.getNodeType(getIndexInPermanentGraph(nodeIndex));
  }

  @NotNull
  @Override
  public GraphEdge.Type getEdgeType(int upNodeIndex, int downNodeIndex) {
    int upIndex = getIndexInPermanentGraph(upNodeIndex);
    if (downNodeIndex == LinearGraph.NOT_LOAD_COMMIT)
      return GraphEdge.Type.USUAL;

    int downIndex = getIndexInPermanentGraph(downNodeIndex);
    return myDelegateGraph.getEdgeType(upIndex, downIndex);
  }

  @Override
  public int nodesCount() {
    return myIntToIntMap.shortSize();
  }

  protected int getIndexInPermanentGraph(int nodeIndex) {
    return myIntToIntMap.getLongIndex(nodeIndex);
  }

  @NotNull
  public IntToIntMap getIntToIntMap() {
    return myIntToIntMap;
  }

  @NotNull
  @Override
  public List<Integer> getUpNodes(int nodeIndex) {
    List<Integer> upDelegateNodes = myDelegateGraph.getUpNodes(getIndexInPermanentGraph(nodeIndex));
    return new ShortNodeIndexList(upDelegateNodes);
  }

  @NotNull
  @Override
  public List<Integer> getDownNodes(int nodeIndex) {
    List<Integer> downDelegateNodes = myDelegateGraph.getDownNodes(getIndexInPermanentGraph(nodeIndex));
    return new ShortNodeIndexList(downDelegateNodes);
  }

  private class ShortNodeIndexList extends AbstractList<Integer> {
    private final List<Integer> longIndexNodes;

    private ShortNodeIndexList(List<Integer> longIndexNodes) {
      this.longIndexNodes = longIndexNodes;
    }

    @Override
    public Integer get(int index) {
      Integer longIndex = longIndexNodes.get(index);
      if (longIndex == LinearGraph.NOT_LOAD_COMMIT)
        return LinearGraph.NOT_LOAD_COMMIT;
      return myIntToIntMap.getShortIndex(longIndex);
    }

    @Override
    public int size() {
      return longIndexNodes.size();
    }
  }
}
