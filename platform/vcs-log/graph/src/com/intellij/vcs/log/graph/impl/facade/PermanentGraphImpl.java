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

package com.intellij.vcs.log.graph.impl.facade;


import com.intellij.openapi.util.Condition;
import com.intellij.vcs.log.graph.GraphColorManager;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutBuilder;
import com.intellij.vcs.log.graph.impl.permanent.PermanentCommitsInfo;
import com.intellij.vcs.log.graph.impl.permanent.PermanentLinearGraphBuilder;
import com.intellij.vcs.log.graph.impl.permanent.PermanentLinearGraphImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PermanentGraphImpl<CommitId> implements PermanentGraph<CommitId> {

  private final Set<Integer> myBranchNodeIndexes;

  @NotNull
  public static <CommitId> PermanentGraphImpl<CommitId> newInstance(@NotNull List<? extends GraphCommit<CommitId>> graphCommits,
                                                                    @NotNull final GraphColorManager<CommitId> graphColorManager,
                                                                    @NotNull Set<CommitId> branchesCommitId) {
    PermanentLinearGraphBuilder<CommitId> permanentLinearGraphBuilder = PermanentLinearGraphBuilder.newInstance(graphCommits);
    PermanentLinearGraphImpl linearGraph = permanentLinearGraphBuilder.build();
    Map<CommitId, GraphCommit<CommitId>> commitsWithNotLoadParent = permanentLinearGraphBuilder.getCommitsWithNotLoadParent();

    final PermanentCommitsInfo<CommitId> commitIdPermanentCommitsInfo = PermanentCommitsInfo.newInstance(graphCommits);

    GraphLayout permanentGraphLayout = GraphLayoutBuilder.build(linearGraph, new Comparator<Integer>() {
      @Override
      public int compare(@NotNull Integer nodeIndex1, @NotNull Integer nodeIndex2) {
        CommitId commitId1 = commitIdPermanentCommitsInfo.getCommitId(nodeIndex1);
        CommitId commitId2 = commitIdPermanentCommitsInfo.getCommitId(nodeIndex2);
        return graphColorManager.compareHeads(commitId1, commitId2);
      }
    });

    return new PermanentGraphImpl<CommitId>(linearGraph, permanentGraphLayout, commitIdPermanentCommitsInfo, graphColorManager,
                                            branchesCommitId, commitsWithNotLoadParent);
  }

  @NotNull
  private final PermanentCommitsInfo<CommitId> myPermanentCommitsInfo;
  @NotNull
  private final PermanentLinearGraphImpl myPermanentLinearGraph;
  @NotNull
  private final GraphLayout myPermanentGraphLayout;
  @NotNull
  private final GraphColorManager<CommitId> myGraphColorManager;
  @NotNull
  private final Set<CommitId> myBranchesCommitId;
  @NotNull
  private final Map<CommitId, GraphCommit<CommitId>> myCommitsWithNotLoadParent;
  @NotNull
  private final ContainingBranchesGetter myBranchesGetter;

  public PermanentGraphImpl(@NotNull PermanentLinearGraphImpl permanentLinearGraph,
                            @NotNull GraphLayout permanentGraphLayout,
                            @NotNull PermanentCommitsInfo<CommitId> permanentCommitsInfo,
                            @NotNull GraphColorManager<CommitId> graphColorManager,
                            @NotNull Set<CommitId> branchesCommitId,
                            @NotNull Map<CommitId, GraphCommit<CommitId>> commitsWithNotLoadParent) {
    myPermanentGraphLayout = permanentGraphLayout;
    myPermanentCommitsInfo = permanentCommitsInfo;
    myPermanentLinearGraph = permanentLinearGraph;
    myGraphColorManager = graphColorManager;
    myBranchesCommitId = branchesCommitId;
    myCommitsWithNotLoadParent = commitsWithNotLoadParent;
    myBranchNodeIndexes = permanentCommitsInfo.convertToCommitIndexes(branchesCommitId);
    myBranchesGetter = new ContainingBranchesGetter(permanentLinearGraph, myBranchNodeIndexes);
  }

  @NotNull
  @Override
  public VisibleGraph<CommitId> setFilter(@Nullable Set<CommitId> headsOfVisibleBranches, @Nullable Condition<CommitId> filter) {
    if (filter == null) {
      return CollapsedVisibleGraph.newInstance(this, headsOfVisibleBranches);
    } else {
      return FilterVisibleGraph.newInstance(this, headsOfVisibleBranches, filter);
    }
  }

  @NotNull
  @Override
  public List<GraphCommit<CommitId>> getAllCommits() {
    return new AbstractList<GraphCommit<CommitId>>() {
      @Override
      public GraphCommit<CommitId> get(int index) {
        CommitId commitId = myPermanentCommitsInfo.getCommitId(index);
        GraphCommit<CommitId> graphCommit = myCommitsWithNotLoadParent.get(commitId);
        if (graphCommit != null)
          return graphCommit;
        List<CommitId> parentsCommitIds = myPermanentCommitsInfo.convertToCommitIdList(myPermanentLinearGraph.getDownNodes(index));
        return new SimpleGraphCommit<CommitId>(commitId, parentsCommitIds, myPermanentCommitsInfo.getTimestamp(index));
      }

      @Override
      public int size() {
        return myPermanentLinearGraph.nodesCount();
      }
    };
  }

  @NotNull
  @Override
  public List<CommitId> getChildren(@NotNull CommitId commit) {
    int commitIndex = myPermanentCommitsInfo.getPermanentNodeIndex(commit);
    return myPermanentCommitsInfo.convertToCommitIdList(myPermanentLinearGraph.getUpNodes(commitIndex));
  }

  @NotNull
  @Override
  public Set<CommitId> getContainingBranches(@NotNull CommitId commit) {
    int commitIndex = myPermanentCommitsInfo.getPermanentNodeIndex(commit);
    return myPermanentCommitsInfo.convertToCommitIdSet(myBranchesGetter.getBranchNodeIndexes(commitIndex));
  }

  @NotNull
  public PermanentCommitsInfo<CommitId> getPermanentCommitsInfo() {
    return myPermanentCommitsInfo;
  }

  @NotNull
  public PermanentLinearGraphImpl getPermanentLinearGraph() {
    return myPermanentLinearGraph;
  }

  @NotNull
  public GraphLayout getPermanentGraphLayout() {
    return myPermanentGraphLayout;
  }

  @NotNull
  public GraphColorManager<CommitId> getGraphColorManager() {
    return myGraphColorManager;
  }

  @NotNull
  public Set<CommitId> getBranchesCommitId() {
    return myBranchesCommitId;
  }

  public Set<Integer> getBranchNodeIndexes() {
    return myBranchNodeIndexes;
  }

  public Condition<Integer> getNotCollapsedNodes() {
    return new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return myBranchNodeIndexes.contains(integer);
      }
    };
  }

  @NotNull
  public Map<CommitId, GraphCommit<CommitId>> getCommitsWithNotLoadParent() {
    return myCommitsWithNotLoadParent;
  }

  private static class SimpleGraphCommit<CommitId> implements GraphCommit<CommitId> {
    @NotNull
    private final CommitId myId;
    @NotNull
    private final List<CommitId> myParents;
    private final long myTimestamp;

    private SimpleGraphCommit(@NotNull CommitId id, @NotNull List<CommitId> parents, long timestamp) {
      myId = id;
      myParents = parents;
      myTimestamp = timestamp;
    }

    @NotNull
    @Override
    public CommitId getId() {
      return myId;
    }

    @NotNull
    @Override
    public List<CommitId> getParents() {
      return myParents;
    }

    @Override
    public long getTimestamp() {
      return myTimestamp;
    }
  }
}
