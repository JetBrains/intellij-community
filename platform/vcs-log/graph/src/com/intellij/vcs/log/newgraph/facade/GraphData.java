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
import com.intellij.util.containers.HashSet;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.newgraph.GraphFlags;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.PermanentGraphLayout;
import com.intellij.vcs.log.newgraph.gpaph.MutableGraph;
import com.intellij.vcs.log.newgraph.gpaph.impl.CollapsedMutableGraph;
import com.intellij.vcs.log.newgraph.gpaph.impl.FilterMutableGraph;
import com.intellij.vcs.log.newgraph.render.ElementColorManager;
import com.intellij.vcs.log.newgraph.render.GraphRender;
import com.intellij.vcs.log.newgraph.render.cell.FilterGraphCellGenerator;
import com.intellij.vcs.log.newgraph.render.cell.GraphCellGeneratorImpl;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class GraphData {

  @NotNull
  private final GraphFlags myGraphFlags;

  @NotNull
  private final PermanentGraph myPermanentGraph;

  @NotNull
  private final Map<Integer,GraphCommit> myCommitsWithNotLoadParentMap;

  @NotNull
  private final PermanentGraphLayout myPermanentGraphLayout;

  @NotNull
  private final DfsUtil myDfsUtil;

  @NotNull
  private final ElementColorManager myColorManager;

  // contains node index in permanent graph
  @NotNull
  private final Set<Integer> myBranchNodeIndexes;

  @NotNull
  private final CurrentBranches myCurrentBranches;

  @NotNull
  private final ContainingBranchesGetter myContainingBranchesGetter;

  @NotNull
  private MutableGraph myMutableGraph;

  @NotNull
  private GraphRender myGraphRender;

  @Nullable
  private Set<Integer> myHeads = null;

  @Nullable
  private Condition<Integer> myVisibilityPredicate = null;

  public GraphData(@NotNull GraphFlags graphFlags,
                   @NotNull PermanentGraph permanentGraph, @NotNull Map<Integer, GraphCommit> commitsWithNotLoadParentMap, @NotNull PermanentGraphLayout permanentGraphLayout,
                   @NotNull ElementColorManager colorManager,
                   @NotNull DfsUtil dfsUtil, @NotNull Set<Integer> branchNodeIndexes) {
    myGraphFlags = graphFlags;
    myPermanentGraph = permanentGraph;
    myCommitsWithNotLoadParentMap = commitsWithNotLoadParentMap;
    myPermanentGraphLayout = permanentGraphLayout;
    myColorManager = colorManager;
    myDfsUtil = dfsUtil;
    myBranchNodeIndexes = branchNodeIndexes;
    myCurrentBranches = new CurrentBranches(permanentGraph, graphFlags.getVisibleNodesInBranches(), dfsUtil);
    myContainingBranchesGetter = new ContainingBranchesGetter(myPermanentGraph, branchNodeIndexes, dfsUtil, graphFlags.getTempFlags());
    applyFilters();
  }

  public void setVisibleBranches(@Nullable Collection<Integer> heads) {
    myHeads = heads == null ? null : new HashSet<Integer>(heads);
    applyFilters();
  }

  public void setFilter(@Nullable Condition<Integer> visibilityPredicate) {
    myVisibilityPredicate = visibilityPredicate;
    applyFilters();
  }

  private void applyFilters() {
    myCurrentBranches.setVisibleBranches(myHeads);
    if (myVisibilityPredicate == null) {
      myMutableGraph = CollapsedMutableGraph.newInstance(myPermanentGraph, myPermanentGraphLayout, myGraphFlags, myBranchNodeIndexes, myDfsUtil);
      GraphCellGeneratorImpl cellGenerator = new GraphCellGeneratorImpl(myMutableGraph);
      myGraphRender = new GraphRender(myMutableGraph, myColorManager, cellGenerator);
    } else {
      Condition<Integer> isVisibleNode = new Condition<Integer>() {
        @Override
        public boolean value(Integer integer) {
          return myVisibilityPredicate.value(myPermanentGraph.getHashIndex(integer));
        }
      };
      FilterMutableGraph filterMutableGraph =
        FilterMutableGraph.newInstance(myPermanentGraph, myPermanentGraphLayout, myGraphFlags.getVisibleNodesInBranches(), myGraphFlags.getFlagsForFilters(), isVisibleNode);
      myMutableGraph = filterMutableGraph;
      FilterGraphCellGenerator filterGraphCellGenerator = new FilterGraphCellGenerator(filterMutableGraph);
      myGraphRender = new GraphRender(myMutableGraph, myColorManager, filterGraphCellGenerator);
    }
  }

  @NotNull
  public PermanentGraph getPermanentGraph() {
    return myPermanentGraph;
  }

  @NotNull
  public Map<Integer, GraphCommit> getCommitsWithNotLoadParentMap() {
    return myCommitsWithNotLoadParentMap;
  }

  @NotNull
  public PermanentGraphLayout getPermanentGraphLayout() {
    return myPermanentGraphLayout;
  }

  @NotNull
  public ContainingBranchesGetter getContainingBranchesGetter() {
    return myContainingBranchesGetter;
  }

  @NotNull
  public MutableGraph getMutableGraph() {
    return myMutableGraph;
  }

  @NotNull
  public GraphRender getGraphRender() {
    return myGraphRender;
  }

  public int getCountVisibleNodes() {
    return getMutableGraph().getCountVisibleNodes();
  }

  public void assertRange(int visibleRowIndex) {
    if (visibleRowIndex < 0 || visibleRowIndex >= getCountVisibleNodes()) {
      throw new IllegalArgumentException("Row not exist! Request row index: " + visibleRowIndex + ", count rows: " + getCountVisibleNodes());
    }
  }
}
