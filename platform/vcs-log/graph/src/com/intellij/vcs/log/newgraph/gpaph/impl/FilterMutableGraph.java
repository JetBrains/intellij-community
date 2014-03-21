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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.util.BooleanFunction;
import com.intellij.util.SmartList;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.PermanentGraphLayout;
import com.intellij.vcs.log.newgraph.SomeGraph;
import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.GraphWithElementsInfo;
import com.intellij.vcs.log.newgraph.gpaph.Node;
import com.intellij.vcs.log.newgraph.gpaph.ThickHoverController;
import com.intellij.vcs.log.newgraph.gpaph.actions.InternalGraphAction;
import com.intellij.vcs.log.facade.utils.Flags;
import com.intellij.vcs.log.facade.utils.IntToIntMap;
import com.intellij.vcs.log.facade.utils.UpdatableIntToIntMap;
import com.intellij.vcs.log.facade.utils.impl.ListIntToIntMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FilterMutableGraph extends MutableGraphWithHiddenNodes<FilterMutableGraph.GraphWithElementsInfoImpl> {
  private static final Logger LOG = Logger.getInstance(FilterMutableGraph.class);

  public static FilterMutableGraph newInstance(@NotNull PermanentGraph permanentGraph,
                                               @NotNull PermanentGraphLayout layout,
                                               @NotNull final Flags visibleNodesInBranches,
                                               @NotNull final Flags visibleNodes,
                                               @NotNull Condition<Integer> isVisibleNode) {

    for (int i = 0; i < permanentGraph.nodesCount(); i++) {
      if (isVisibleNode.value(i) && !visibleNodesInBranches.get(i))
        LOG.debug("Filter give me commit, which hidden in current branches visibility; commitHashIndex: " + permanentGraph.getHashIndex(i));

      visibleNodes.set(i, isVisibleNode.value(i));
    }
    UpdatableIntToIntMap visibleToReal = ListIntToIntMap.newInstance(new BooleanFunction<Integer>() {
      @Override
      public boolean fun(Integer integer) {
        return visibleNodes.get(integer);
      }
    }, permanentGraph.nodesCount());

    return new FilterMutableGraph(permanentGraph, layout, isVisibleNode, visibleToReal, visibleNodes);
  }

  @NotNull
  private final Flags myVisibleNodes;

  @NotNull
  private final PermanentGraph myPermanentGraph;

  @NotNull
  private final Condition<Integer> isVisibleNode;

  @NotNull
  private final FilterThickHoverController myThickHoverController;

  @NotNull
  private final UpdatableIntToIntMap myUpdatableIntToIntMap;


  private FilterMutableGraph(@NotNull PermanentGraph permanentGraph,
                            @NotNull PermanentGraphLayout layout,
                            @NotNull Condition<Integer> isVisibleNode,
                            @NotNull UpdatableIntToIntMap visibleToReal,
                            @NotNull Flags visibleNodes) {
    super(visibleToReal, new GraphWithElementsInfoImpl(permanentGraph, visibleNodes, visibleToReal), layout);
    myUpdatableIntToIntMap = visibleToReal;
    myVisibleNodes = visibleNodes;
    myPermanentGraph = permanentGraph;
    this.isVisibleNode = isVisibleNode;
    myThickHoverController = new FilterThickHoverController();
  }


  @Override
  public int performAction(@NotNull InternalGraphAction action) {
    myThickHoverController.performAction(action);

    return -1;
  }

  public boolean nextRowIsHide(int visibleRowIndex) {
    int realIndex = myVisibleToReal.getLongIndex(visibleRowIndex);
    return realIndex < myPermanentGraph.nodesCount() - 1 && ! myVisibleNodes.get(realIndex + 1);
  }

  // for future
  @SuppressWarnings("unused")
  private void showHideFragment(int visibleRowIndex) {
    int startIndex = myVisibleToReal.getLongIndex(visibleRowIndex);
    int endIndex;
    for (endIndex = startIndex + 1; endIndex < myPermanentGraph.nodesCount(); endIndex++) {
      if (myVisibleNodes.get(endIndex)) {
        break;
      } else {
        myVisibleNodes.set(endIndex, true);
      }
    }
    if (endIndex == myPermanentGraph.nodesCount())
      endIndex--;

    myUpdatableIntToIntMap.update(startIndex, endIndex);
  }

  @NotNull
  @Override
  public ThickHoverController getThickHoverController() {
    return myThickHoverController;
  }

  protected static class GraphWithElementsInfoImpl implements GraphWithElementsInfo {
    private final PermanentGraph myGraph;
    private final Flags myVisibleNodes;
    private final IntToIntMap myIntToIntMap;

    private GraphWithElementsInfoImpl(PermanentGraph graph, Flags visibleNodes, IntToIntMap intToIntMap) {
      myGraph = graph;
      myVisibleNodes = visibleNodes;
      myIntToIntMap = intToIntMap;
    }

    @NotNull
    @Override
    public Node.Type getNodeType(int nodeIndex) {
      return Node.Type.USUAL;
    }

    @NotNull
    @Override
    public Edge.Type getEdgeType(int upNodeIndex, int downNodeIndex) {
      return Edge.Type.USUAL;
    }

    @Override
    public int nodesCount() {
      return myIntToIntMap.shortSize();
    }

    @NotNull
    @Override
    public List<Integer> getUpNodes(int nodeIndex) {
      List<Integer> result = new SmartList<Integer>();
      for (int upNode : myGraph.getUpNodes(nodeIndex)) {
        if (myVisibleNodes.get(upNode))
          result.add(upNode);
      }

      return result;
    }

    @NotNull
    @Override
    public List<Integer> getDownNodes(int nodeIndex) {
      List<Integer> result = new SmartList<Integer>();
      for (int downNode : myGraph.getDownNodes(nodeIndex)) {
        if (downNode != SomeGraph.NOT_LOAD_COMMIT && myVisibleNodes.get(downNode))
          result.add(downNode);
      }

      return result;
    }
  }
}
