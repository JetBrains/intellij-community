// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.util.NotNullFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.GraphCommitImpl;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutImpl;
import com.intellij.vcs.log.graph.impl.permanent.PermanentCommitsInfoImpl;
import com.intellij.vcs.log.graph.impl.permanent.PermanentLinearGraphBuilder;
import com.intellij.vcs.log.graph.impl.permanent.PermanentLinearGraphImpl;
import com.intellij.vcs.log.graph.utils.IntList;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import com.intellij.vcs.log.graph.utils.impl.CompressedIntList;
import com.intellij.vcs.log.graph.utils.impl.IntTimestampGetter;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.asLiteLinearGraph;

public class SimpleGraphInfo<CommitId> implements PermanentGraphInfo<CommitId> {
  @NotNull private final LinearGraph myLinearGraph;
  @NotNull private final GraphLayout myGraphLayout;
  @NotNull private final NotNullFunction<? super Integer, ? extends CommitId> myFunction;
  @NotNull private final TimestampGetter myTimestampGetter;
  @NotNull private final Set<Integer> myBranchNodeIds;

  private SimpleGraphInfo(@NotNull LinearGraph linearGraph,
                          @NotNull GraphLayout graphLayout,
                          @NotNull NotNullFunction<? super Integer, ? extends CommitId> function,
                          @NotNull TimestampGetter timestampGetter,
                          @NotNull Set<Integer> branchNodeIds) {
    myLinearGraph = linearGraph;
    myGraphLayout = graphLayout;
    myFunction = function;
    myTimestampGetter = timestampGetter;
    myBranchNodeIds = branchNodeIds;
  }

  public static <CommitId> SimpleGraphInfo<CommitId> build(@NotNull LinearGraph linearGraph,
                                                           @NotNull GraphLayout oldLayout,
                                                           @NotNull PermanentCommitsInfo<CommitId> permanentCommitsInfo,
                                                           int permanentGraphSize,
                                                           @NotNull Set<Integer> branchNodeIds,
                                                           int visibleRow,
                                                           int visibleRange) {
    int start = Math.max(0, visibleRow - visibleRange);
    int end = Math.min(linearGraph.nodesCount(), start + 2 * visibleRange); // no more than 2*visibleRange commits;

    List<GraphCommit<CommitId>> graphCommits = new ArrayList<>(end - start);
    List<CommitId> commitsIdMap = new ArrayList<>(end - start);

    for (int row = start; row < end; row++) {
      int nodeId = linearGraph.getNodeId(row);
      CommitId commit = permanentCommitsInfo.getCommitId(nodeId);
      List<CommitId> parents = new SmartList<>();
      parents.addAll(ContainerUtil.mapNotNull(asLiteLinearGraph(linearGraph).getNodes(row, LiteLinearGraph.NodeFilter.DOWN),
                                              row1 -> {
                                                if (row1 < start || row1 >= end) return null;
                                                return permanentCommitsInfo.getCommitId(linearGraph.getNodeId(row1));
                                              }));
      graphCommits.add(GraphCommitImpl.createCommit(commit, parents, permanentCommitsInfo.getTimestamp(nodeId)));
      commitsIdMap.add(commit);
    }
    IntTimestampGetter timestampGetter = PermanentCommitsInfoImpl.createTimestampGetter(graphCommits);

    NotNullFunction<Integer, CommitId> commitIdMapping = createCommitIdMapFunction(commitsIdMap);
    PermanentLinearGraphImpl newLinearGraph = PermanentLinearGraphBuilder.newInstance(graphCommits).build();

    int[] layoutIndexes = new int[end - start];
    List<Integer> headNodeIndexes = new ArrayList<>();

    TObjectIntHashMap<CommitId> commitIdToInteger = reverseCommitIdMap(permanentCommitsInfo, permanentGraphSize);
    for (int row = start; row < end; row++) {
      CommitId commitId = commitsIdMap.get(row - start);
      int layoutIndex = oldLayout.getLayoutIndex(commitIdToInteger.get(commitId));
      layoutIndexes[row - start] = layoutIndex;
      if (asLiteLinearGraph(newLinearGraph).getNodes(row - start, LiteLinearGraph.NodeFilter.UP).isEmpty()) {
        headNodeIndexes.add(row - start);
      }
    }

    ContainerUtil.sort(headNodeIndexes, Comparator.comparingInt(o -> layoutIndexes[o]));
    int[] starts = new int[headNodeIndexes.size()];
    for (int i = 0; i < starts.length; i++) {
      starts[i] = layoutIndexes[headNodeIndexes.get(i)];
    }

    GraphLayoutImpl newLayout = new GraphLayoutImpl(layoutIndexes, headNodeIndexes, starts);

    return new SimpleGraphInfo<>(newLinearGraph, newLayout, commitIdMapping, timestampGetter,
                                 LinearGraphUtils.convertIdsToNodeIndexes(linearGraph, branchNodeIds));
  }

  @NotNull
  private static <CommitId> NotNullFunction<Integer, CommitId> createCommitIdMapFunction(@NotNull List<? extends CommitId> commitsIdMap) {
    if (!commitsIdMap.isEmpty() && commitsIdMap.get(0) instanceof Integer) {
      int[] ints = new int[commitsIdMap.size()];
      for (int row = 0; row < commitsIdMap.size(); row++) {
        ints[row] = (Integer)commitsIdMap.get(row);
      }
      return (NotNullFunction<Integer, CommitId>)new IntegerCommitIdMapFunction(CompressedIntList.newInstance(ints));
    }
    return new CommitIdMapFunction<>(commitsIdMap);
  }

  @NotNull
  private static <CommitId> TObjectIntHashMap<CommitId> reverseCommitIdMap(@NotNull PermanentCommitsInfo<CommitId> permanentCommitsInfo,
                                                                           int size) {
    TObjectIntHashMap<CommitId> result = new TObjectIntHashMap<>();
    for (int i = 0; i < size; i++) {
      result.put(permanentCommitsInfo.getCommitId(i), i);
    }
    return result;
  }

  @NotNull
  @Override
  public PermanentCommitsInfo<CommitId> getPermanentCommitsInfo() {
    return new PermanentCommitsInfo<CommitId>() {
      @NotNull
      @Override
      public CommitId getCommitId(int nodeId) {
        return myFunction.fun(nodeId);
      }

      @Override
      public long getTimestamp(int nodeId) {
        return myTimestampGetter.getTimestamp(nodeId);
      }

      @Override
      public int getNodeId(@NotNull CommitId commitId) {
        for (int id = 0; id < myLinearGraph.nodesCount(); id++) {
          if (myFunction.fun(id).equals(commitId)) {
            return id;
          }
        }
        return -1;
      }

      @NotNull
      @Override
      public Set<Integer> convertToNodeIds(@NotNull Collection<? extends CommitId> heads) {
        Set<Integer> result = new HashSet<>();
        for (int id = 0; id < myLinearGraph.nodesCount(); id++) {
          if (heads.contains(myFunction.fun(id))) {
            result.add(id);
          }
        }
        return result;
      }
    };
  }

  @NotNull
  @Override
  public LinearGraph getLinearGraph() {
    return myLinearGraph;
  }

  @NotNull
  @Override
  public GraphLayout getPermanentGraphLayout() {
    return myGraphLayout;
  }

  @NotNull
  @Override
  public Set<Integer> getBranchNodeIds() {
    return myBranchNodeIds;
  }

  private static class CommitIdMapFunction<CommitId> implements NotNullFunction<Integer, CommitId> {
    private final List<? extends CommitId> myCommitsIdMap;

    CommitIdMapFunction(List<? extends CommitId> commitsIdMap) {
      myCommitsIdMap = commitsIdMap;
    }

    @NotNull
    @Override
    public CommitId fun(Integer dom) {
      return myCommitsIdMap.get(dom);
    }
  }

  private static class IntegerCommitIdMapFunction implements NotNullFunction<Integer, Integer> {
    private final IntList myCommitsIdMap;

    IntegerCommitIdMapFunction(IntList commitsIdMap) {
      myCommitsIdMap = commitsIdMap;
    }

    @NotNull
    @Override
    public Integer fun(Integer dom) {
      return myCommitsIdMap.get(dom);
    }
  }
}
