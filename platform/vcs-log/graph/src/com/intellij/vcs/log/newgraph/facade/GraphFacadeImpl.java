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

package com.intellij.vcs.log.newgraph.facade;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBColor;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.newgraph.GraphFlags;
import com.intellij.vcs.log.newgraph.PermanentGraphLayout;
import com.intellij.vcs.log.newgraph.gpaph.GraphElement;
import com.intellij.vcs.log.newgraph.impl.PermanentGraphBuilder;
import com.intellij.vcs.log.newgraph.impl.PermanentGraphImpl;
import com.intellij.vcs.log.newgraph.impl.PermanentGraphLayoutBuilder;
import com.intellij.vcs.log.newgraph.render.ElementColorManager;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GraphFacadeImpl implements GraphFacade {
  @NotNull
  public static GraphFacadeImpl newInstance(@NotNull List<? extends GraphCommit> commits,
                                            @NotNull Set<Integer> branchCommitHashIndexes,
                                            @NotNull final GraphColorManager colorManager) {
    GraphFlags flags = new GraphFlags(commits.size());
    Pair<PermanentGraphImpl,Map<Integer,GraphCommit>> graphAndUnderdoneCommits = PermanentGraphBuilder.build(flags.getSimpleNodeFlags(), commits);
    final PermanentGraphImpl permanentGraph = graphAndUnderdoneCommits.first;

    DfsUtil dfsUtil = new DfsUtil(commits.size());

    final PermanentGraphLayout graphLayout = PermanentGraphLayoutBuilder.build(dfsUtil, permanentGraph, new Comparator<Integer>() {
      @Override
      public int compare(@NotNull Integer o1, @NotNull Integer o2) {
        int hashIndex1 = permanentGraph.getHashIndex(o1);
        int hashIndex2 = permanentGraph.getHashIndex(o2);
        return colorManager.compareHeads(hashIndex1, hashIndex2);
      }
    });

    ElementColorManager elementColorManager = new ElementColorManager() {
      @NotNull
      @Override
      public JBColor getColor(@NotNull GraphElement element) {
        int headNodeIndex = graphLayout.getHeadNodeIndex(element.getLayoutIndex());
        int headHashIndex = permanentGraph.getHashIndex(headNodeIndex);
        int baseLayoutIndex = graphLayout.getStartLayout(element.getLayoutIndex());
        if (baseLayoutIndex == element.getLayoutIndex()) {
          return colorManager.getColorOfBranch(headHashIndex);
        } else {
          return colorManager.getColorOfFragment(headHashIndex, element.getLayoutIndex());
        }
      }
    };

    Set<Integer> branchNodeIndexes = new HashSet<Integer>();
    for (int i = 0; i < permanentGraph.nodesCount(); i++) {
      if (branchCommitHashIndexes.contains(permanentGraph.getHashIndex(i)))
        branchNodeIndexes.add(i);
    }
    GraphData graphData = new GraphData(flags, permanentGraph, graphAndUnderdoneCommits.second, graphLayout, elementColorManager, dfsUtil,
                                        branchNodeIndexes);
    return new GraphFacadeImpl(graphData);
  }

  @NotNull
  private final GraphData myGraphData;

  @NotNull
  private final GraphActionDispatcher myActionDispatcher;

  public GraphFacadeImpl(@NotNull GraphData graphData) {
    myGraphData = graphData;
    myActionDispatcher = new GraphActionDispatcher(graphData);
  }

  @NotNull
  @Override
  public PaintInfo paint(int visibleRow) {
    myGraphData.assertRange(visibleRow);
    return myGraphData.getGraphRender().paint(visibleRow);
  }

  @Nullable
  @Override
  public GraphAnswer performAction(@NotNull GraphAction action) {
    return myActionDispatcher.performAction(action);
  }

  @NotNull
  @Override
  public List<Integer> getAllCommits() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCommitAtRow(int visibleRow) {
    myGraphData.assertRange(visibleRow);
    int indexInPermanentGraph = myGraphData.getMutableGraph().getIndexInPermanentGraph(visibleRow);
    return myGraphData.getPermanentGraph().getHashIndex(indexInPermanentGraph);
  }

  @Override
  public int getVisibleCommitCount() {
    return myGraphData.getCountVisibleNodes();
  }

  @Override
  public void setVisibleBranches(@Nullable Collection<Integer> heads) {
    myGraphData.setVisibleBranches(heads);
  }

  @Override
  public void setFilter(@Nullable Condition<Integer> visibilityPredicate) {
    myGraphData.setFilter(visibilityPredicate);
  }

  @NotNull
  @Override
  public GraphInfoProvider getInfoProvider() {
    return new GraphInfoProvider() {
      @NotNull
      @Override
      public Set<Integer> getContainingBranches(int visibleRow) {
        myGraphData.assertRange(visibleRow);
        return myGraphData.getContainingBranchesGetter()
          .getBranchHashIndexes(myGraphData.getMutableGraph().getIndexInPermanentGraph(visibleRow));
      }

      @NotNull
      @Override
      public RowInfo getRowInfo(int visibleRow) {
        myGraphData.assertRange(visibleRow);
        int indexInPermanentGraph = myGraphData.getMutableGraph().getIndexInPermanentGraph(visibleRow);
        final int oneOfHeadNodeIndex = myGraphData.getPermanentGraphLayout().getOneOfHeadNodeIndex(indexInPermanentGraph);
        return new RowInfo() {
          @Override
          public int getOneOfHeads() {
            return myGraphData.getPermanentGraph().getHashIndex(oneOfHeadNodeIndex);
          }
        };
      }

      @Override
      public boolean areLongEdgesHidden() {
        return !myGraphData.getGraphRender().isShowLongEdges();
      }
    };
  }
}
