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
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.newgraph.GraphFlags;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.PermanentGraphLayout;
import com.intellij.vcs.log.newgraph.gpaph.MutableGraph;
import com.intellij.vcs.log.newgraph.gpaph.impl.PermanentAsMutableGraph;
import com.intellij.vcs.log.newgraph.gpaph.impl.ThickHoverControllerTest;
import com.intellij.vcs.log.newgraph.impl.PermanentGraphBuilder;
import com.intellij.vcs.log.newgraph.impl.PermanentGraphImpl;
import com.intellij.vcs.log.newgraph.impl.PermanentGraphLayoutBuilder;
import com.intellij.vcs.log.newgraph.render.GraphRender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GraphFacadeImpl implements GraphFacade {
  @NotNull
  public static GraphFacadeImpl newInstance(@NotNull List<? extends GraphCommit> commits) {
    long ms;
    ms = System.currentTimeMillis();
    GraphFlags flags = new GraphFlags(commits.size());
    PermanentGraphImpl permanentGraph = PermanentGraphBuilder.build(flags.getSimpleNodeFlags(), commits);
    System.out.println("PermanentGraph:" + (System.currentTimeMillis() - ms));


    ms = System.currentTimeMillis();
    PermanentGraphLayout graphLayout = PermanentGraphLayoutBuilder.build(permanentGraph);
    System.out.println("LayoutModel:" + (System.currentTimeMillis() - ms));


    ms = System.currentTimeMillis();
    for (int i = 0; i < permanentGraph.nodesCount(); i++) {
      graphLayout.getOneOfHeadNodeIndex(i);
    }
    System.out.println("getOneOfHead:" + (System.currentTimeMillis() - ms));

    ms = System.currentTimeMillis();
    List<Integer> headers = new ArrayList<Integer>();
    for (int i = 0; i < permanentGraph.nodesCount(); i++) {
      if (permanentGraph.getUpNodes(i).size() == 0) {
        headers.add(i);
      }
    }
    System.out.println("graph walk:" + (System.currentTimeMillis() - ms));

    PermanentAsMutableGraph mutableGraph = new PermanentAsMutableGraph(permanentGraph, graphLayout);
    GraphRender graphRender = new GraphRender(mutableGraph, new ThickHoverControllerTest());
    return new GraphFacadeImpl(flags, permanentGraph, graphLayout, mutableGraph, graphRender);
  }

  @NotNull
  private final GraphFlags myGraphFlags;

  @NotNull
  private final PermanentGraph myPermanentGraph;

  @NotNull
  private final PermanentGraphLayout myPermanentGraphLayout;

  @NotNull
  private final MutableGraph myMutableGraph;
  @NotNull
  private final GraphRender myGraphRender;

  public GraphFacadeImpl(@NotNull GraphFlags graphFlags, @NotNull PermanentGraph permanentGraph,
                         @NotNull PermanentGraphLayout permanentGraphLayout,
                         @NotNull MutableGraph mutableGraph,
                         @NotNull GraphRender graphRender) {
    myGraphFlags = graphFlags;
    myPermanentGraph = permanentGraph;
    myPermanentGraphLayout = permanentGraphLayout;
    myMutableGraph = mutableGraph;
    myGraphRender = graphRender;
  }

  @NotNull
  @Override
  public PaintInfo paint(int visibleRow) {
    return myGraphRender.paint(visibleRow);
  }

  @Nullable
  @Override
  public GraphAnswer performAction(@NotNull GraphAction action) {
    return null;
  }

  @NotNull
  @Override
  public List<Integer> getAllCommits() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCommitAtRow(int visibleRow) {
    int indexInPermanentGraph = myMutableGraph.getIndexInPermanentGraph(visibleRow);
    return myPermanentGraph.getHashIndex(indexInPermanentGraph);
  }

  @Override
  public int getVisibleCommitCount() {
    return myMutableGraph.getCountVisibleNodes();
  }

  @Override
  public void setVisibleBranches(@Nullable Collection<Integer> heads) {

  }

  @Override
  public void setFilter(@NotNull Condition<Integer> visibilityPredicate) {

  }

  @NotNull
  @Override
  public GraphInfoProvider getInfoProvider() {
    return new GraphInfoProvider() {
      @Override
      public Set<Integer> getContainingBranches(int visibleRow) {
        return Collections.emptySet();
      }

      @Override
      public RowInfo getRowInfo(int visibleRow) {
        int indexInPermanentGraph = myMutableGraph.getIndexInPermanentGraph(visibleRow);
        final int oneOfHeadNodeIndex = myPermanentGraphLayout.getOneOfHeadNodeIndex(indexInPermanentGraph);
        return new RowInfo() {
          @Override
          public int getOneOfHeads() {
            return myPermanentGraph.getHashIndex(oneOfHeadNodeIndex);
          }
        };
      }

      @Override
      public boolean areLongEdgesHidden() {
        return myGraphRender.areLongEdgesHidden();
      }
    };
  }
}
