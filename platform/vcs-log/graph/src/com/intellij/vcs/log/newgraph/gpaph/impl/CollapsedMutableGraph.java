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

import com.intellij.util.BooleanFunction;
import com.intellij.vcs.log.graph.impl.visible.GraphWithElementsInfoImpl;
import com.intellij.vcs.log.newgraph.GraphFlags;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.gpaph.*;
import com.intellij.vcs.log.newgraph.gpaph.actions.ClickInternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.actions.InternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.actions.LinearBranchesExpansionInternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.fragments.FragmentGenerator;
import com.intellij.vcs.log.newgraph.gpaph.fragments.GraphFragment;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.UpdatableIntToIntMap;
import com.intellij.vcs.log.graph.utils.impl.ListIntToIntMap;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.vcs.log.newgraph.utils.MyUtils.setAllValues;

public class CollapsedMutableGraph extends MutableGraphWithHiddenNodes<GraphWithElementsInfoImpl> {

  public static CollapsedMutableGraph newInstance(@NotNull PermanentGraph permanentGraph,
                                                  @NotNull GraphLayout layout,
                                                  @NotNull GraphFlags graphFlags,
                                                  @NotNull Set<Integer> branchNodeIndexes,
                                                  @NotNull DfsUtil dfsUtil) {
    final Flags visibleNodes = graphFlags.getVisibleNodes();
    final Flags visibleNodesInBranches = graphFlags.getVisibleNodesInBranches();
    setAllValues(visibleNodes, true);
    UpdatableIntToIntMap intToIntMap = ListIntToIntMap.newInstance(new BooleanFunction<Integer>() {
      @Override
      public boolean fun(Integer integer) {
        return visibleNodes.get(integer) && visibleNodesInBranches.get(integer);
      }
    }, permanentGraph.nodesCount());

    GraphWithElementsInfoImpl graphWithElementsInfo =
      new GraphWithElementsInfoImpl(permanentGraph, visibleNodesInBranches, visibleNodes, intToIntMap, dfsUtil);
    return new CollapsedMutableGraph(permanentGraph, layout, intToIntMap, graphFlags.getThickFlags(),
                                     dfsUtil,branchNodeIndexes, graphWithElementsInfo);
  }

  @NotNull
  private final FragmentGenerator myFragmentGenerator;

  @NotNull
  private final AbstractThickHoverController myThickHoverController;

  private CollapsedMutableGraph(@NotNull PermanentGraph permanentGraph,
                                @NotNull GraphLayout layout,
                                @NotNull UpdatableIntToIntMap intToIntMap,
                                @NotNull Flags thickFlags,
                                @NotNull DfsUtil dfsUtil,
                                @NotNull Set<Integer> branchNodeIndexes,
                                @NotNull GraphWithElementsInfoImpl graphWithElementsInfo) {
    super(intToIntMap, graphWithElementsInfo, layout);
    myFragmentGenerator = new FragmentGenerator(this, branchNodeIndexes);
    myThickHoverController = new ThickHoverControllerImpl(permanentGraph, this, myFragmentGenerator, thickFlags, dfsUtil);
  }

  @Override
  public int performAction(@NotNull InternalGraphAction action) {
    myThickHoverController.performAction(action);
    if (action instanceof ClickInternalGraphAction) {
      GraphElement element = ((ClickInternalGraphAction)action).getInfo();
      if (element != null) {
        Edge edge = containedCollapsedEdge(element);
        if (edge != null) {
          myGraph.expand(getIndexInPermanentGraph(edge.getUpNodeVisibleIndex()),
                                         getIndexInPermanentGraph(edge.getDownNodeVisibleIndex()));
          return edge.getUpNodeVisibleIndex();
        }

        GraphFragment fragment = myFragmentGenerator.getLongFragment(element);
        if (fragment != null) {
          myGraph.collapse(getIndexInPermanentGraph(fragment.upVisibleNodeIndex),
                                           getIndexInPermanentGraph(fragment.downVisibleNodeIndex));
          return fragment.upVisibleNodeIndex;
        }
      }
    }

    if (action instanceof LinearBranchesExpansionInternalGraphAction) {
      Boolean info = ((LinearBranchesExpansionInternalGraphAction)action).getInfo();
      assert info != null;
      boolean shouldExpand = info;
      if (shouldExpand)
        myGraph.expandAll();
      else {
        int currentVisibleIndex = 0;
        while (currentVisibleIndex < this.getCountVisibleNodes()) {
          GraphFragment fragment = myFragmentGenerator.getLongDownFragment(currentVisibleIndex);
          if (fragment != null) {
            myGraph.collapse(getIndexInPermanentGraph(fragment.upVisibleNodeIndex),
                             getIndexInPermanentGraph(fragment.downVisibleNodeIndex));
          }
          currentVisibleIndex++;
        }
      }
      return 1;
    }

    return -1;
  }

  @NotNull
  public GraphWithElementsInfo getInternalGraph() {
    return myGraph;
  }


  @NotNull
  @Override
  public ThickHoverController getThickHoverController() {
    return myThickHoverController;
  }

}
