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
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.facade.bek.BekIntMap;
import com.intellij.vcs.log.graph.impl.facade.bek.BekSorter;
import com.intellij.vcs.log.graph.impl.permanent.*;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PermanentGraphImpl<CommitId> implements PermanentGraph<CommitId>, PermanentGraphInfo<CommitId> {

  @NotNull
  public static <CommitId> PermanentGraphImpl<CommitId> newInstance(@NotNull List<? extends GraphCommit<CommitId>> graphCommits,
                                                                    @NotNull final GraphColorManager<CommitId> graphColorManager,
                                                                    @NotNull Set<CommitId> branchesCommitId) {
    PermanentLinearGraphBuilder<CommitId> permanentLinearGraphBuilder = PermanentLinearGraphBuilder.newInstance(graphCommits);
    final Map<Integer, CommitId> notLoadCommits = ContainerUtil.newHashMap();
    PermanentLinearGraphImpl linearGraph = permanentLinearGraphBuilder.build(new NotNullFunction<CommitId, Integer>() {
      @NotNull
      @Override
      public Integer fun(CommitId dom) {
        int nodeId = - (notLoadCommits.size() + 2);
        notLoadCommits.put(nodeId, dom);
        return nodeId;
      }
    });

    final PermanentCommitsInfoIml<CommitId> commitIdPermanentCommitsInfo = PermanentCommitsInfoIml.newInstance(graphCommits, notLoadCommits);

    GraphLayoutImpl permanentGraphLayout = GraphLayoutBuilder.build(linearGraph, new Comparator<Integer>() {
      @Override
      public int compare(@NotNull Integer nodeIndex1, @NotNull Integer nodeIndex2) {
        CommitId commitId1 = commitIdPermanentCommitsInfo.getCommitId(nodeIndex1);
        CommitId commitId2 = commitIdPermanentCommitsInfo.getCommitId(nodeIndex2);
        return graphColorManager.compareHeads(commitId2, commitId1);
      }
    });

    return new PermanentGraphImpl<CommitId>(linearGraph, permanentGraphLayout, commitIdPermanentCommitsInfo, graphColorManager,
                                            branchesCommitId);
  }

  @NotNull
  private final PermanentCommitsInfoIml<CommitId> myPermanentCommitsInfo;
  @NotNull
  private final PermanentLinearGraphImpl myPermanentLinearGraph;
  @NotNull
  private final GraphLayoutImpl myPermanentGraphLayout;
  @NotNull
  private final GraphColorManager<CommitId> myGraphColorManager;
  @NotNull
  private final Set<CommitId> myBranchesCommitId;
  @NotNull
  private final Set<Integer> myBranchNodeIds;
  @NotNull
  private final ContainingBranchesGetter myBranchesGetter;
  @NotNull
  private final BekIntMap myBekIntMap;

  public PermanentGraphImpl(@NotNull PermanentLinearGraphImpl permanentLinearGraph,
                            @NotNull GraphLayoutImpl permanentGraphLayout,
                            @NotNull PermanentCommitsInfoIml<CommitId> permanentCommitsInfo,
                            @NotNull GraphColorManager<CommitId> graphColorManager,
                            @NotNull Set<CommitId> branchesCommitId) {
    myPermanentGraphLayout = permanentGraphLayout;
    myPermanentCommitsInfo = permanentCommitsInfo;
    myPermanentLinearGraph = permanentLinearGraph;
    myGraphColorManager = graphColorManager;
    myBranchesCommitId = branchesCommitId;
    myBranchNodeIds = permanentCommitsInfo.convertToNodeIds(branchesCommitId);
    myBranchesGetter = new ContainingBranchesGetter(permanentLinearGraph, myBranchNodeIds);
    myBekIntMap = BekSorter.createBekMap(myPermanentLinearGraph, myPermanentGraphLayout, myPermanentCommitsInfo.getTimestampGetter());
  }

  @NotNull
  @Override
  public VisibleGraph<CommitId> createVisibleGraph(@NotNull SortType sortType,
                                                   @Nullable Set<CommitId> headsOfVisibleBranches,
                                                   @Nullable Condition<CommitId> filter) {
    CascadeLinearGraphController controller;
    if (sortType == SortType.Bek) {
      controller = new BekBaseLinearGraphController(this, myBekIntMap);
    }
    else {
      controller = new BaseLinearGraphController(this);
    }
    return new VisibleGraphImpl<CommitId>(controller, this);
    //
    //if (filter == null) {
    //  return CollapsedVisibleGraph.newInstance(getBekPermanentGraphInfo(sortType), headsOfVisibleBranches);
    //} else {
    //  return FilterVisibleGraph.newInstance(getBekPermanentGraphInfo(sortType), headsOfVisibleBranches, filter);
    //}
  }

  @NotNull
  @Override
  public List<GraphCommit<CommitId>> getAllCommits() {
    List<GraphCommit<CommitId>> result = ContainerUtil.newArrayList();
    for (int index = 0; index < myPermanentLinearGraph.nodesCount(); index++) {
      CommitId commitId = myPermanentCommitsInfo.getCommitId(index);
      List<Integer> downNodes = LinearGraphUtils.getDownNodesIncludeNotLoad(myPermanentLinearGraph, index);
      List<CommitId> parentsCommitIds = myPermanentCommitsInfo.convertToCommitIdList(downNodes);
      GraphCommit<CommitId> graphCommit = new GraphCommitImpl<CommitId>(commitId, parentsCommitIds, myPermanentCommitsInfo.getTimestamp(index));
      result.add(graphCommit);
    }

    return result;
  }

  @NotNull
  @Override
  public List<CommitId> getChildren(@NotNull CommitId commit) {
    int commitIndex = myPermanentCommitsInfo.getNodeId(commit);
    return myPermanentCommitsInfo.convertToCommitIdList(myPermanentLinearGraph.getUpNodes(commitIndex));
  }

  @NotNull
  @Override
  public Set<CommitId> getContainingBranches(@NotNull CommitId commit) {
    int commitIndex = myPermanentCommitsInfo.getNodeId(commit);
    return myPermanentCommitsInfo.convertToCommitIdSet(myBranchesGetter.getBranchNodeIndexes(commitIndex));
  }

  @NotNull
  public PermanentCommitsInfoIml<CommitId> getPermanentCommitsInfo() {
    return myPermanentCommitsInfo;
  }

  @NotNull
  public LinearGraph getPermanentLinearGraph() {
    return myPermanentLinearGraph;
  }

  @NotNull
  public GraphLayoutImpl getPermanentGraphLayout() {
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

  @NotNull
  public Set<Integer> getBranchNodeIds() {
    return myBranchNodeIds;
  }

  @NotNull
  public Condition<Integer> getNotCollapsedNodes() {
    return new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return myBranchNodeIds.contains(integer);
      }
    };
  }

}
