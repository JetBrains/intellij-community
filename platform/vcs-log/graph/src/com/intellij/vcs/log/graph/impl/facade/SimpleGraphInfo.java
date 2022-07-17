// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade;

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
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.asLiteLinearGraph;

public final class SimpleGraphInfo<CommitId> implements PermanentGraphInfo<CommitId> {
  private final @NotNull LinearGraph myLinearGraph;
  private final @NotNull GraphLayout myGraphLayout;
  private final @NotNull Function<? super Integer, ? extends @NotNull CommitId> myFunction;
  private final @NotNull TimestampGetter myTimestampGetter;
  private final @NotNull Set<Integer> myBranchNodeIds;

  private SimpleGraphInfo(@NotNull LinearGraph linearGraph,
                          @NotNull GraphLayout graphLayout,
                          @NotNull Function<? super Integer, ? extends @NotNull CommitId> function,
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

    Function<Integer, @NotNull CommitId> commitIdMapping = createCommitIdMapFunction(commitsIdMap);
    PermanentLinearGraphImpl newLinearGraph = PermanentLinearGraphBuilder.newInstance(graphCommits).build();

    int[] layoutIndexes = new int[end - start];
    List<Integer> headNodeIndexes = new ArrayList<>();

    Object2IntMap<CommitId> commitIdToInteger = reverseCommitIdMap(permanentCommitsInfo, permanentGraphSize);
    for (int row = start; row < end; row++) {
      CommitId commitId = commitsIdMap.get(row - start);
      int layoutIndex = oldLayout.getLayoutIndex(commitIdToInteger.getInt(commitId));
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

  private static @NotNull <CommitId> Function<Integer, @NotNull CommitId> createCommitIdMapFunction(@NotNull List<? extends CommitId> commitsIdMap) {
    if (!commitsIdMap.isEmpty() && commitsIdMap.get(0) instanceof Integer) {
      int[] ints = new int[commitsIdMap.size()];
      for (int row = 0; row < commitsIdMap.size(); row++) {
        ints[row] = (Integer)commitsIdMap.get(row);
      }
      //noinspection unchecked
      return (Function<Integer, CommitId>)new IntegerCommitIdMapFunction(CompressedIntList.newInstance(ints));
    }
    return new CommitIdMapFunction<>(commitsIdMap);
  }

  private static @NotNull <CommitId> Object2IntMap<CommitId> reverseCommitIdMap(@NotNull PermanentCommitsInfo<CommitId> permanentCommitsInfo,
                                                                                int size) {
    Object2IntMap<CommitId> result = new Object2IntOpenHashMap<>();
    for (int i = 0; i < size; i++) {
      result.put(permanentCommitsInfo.getCommitId(i), i);
    }
    return result;
  }

  @Override
  public @NotNull PermanentCommitsInfo<CommitId> getPermanentCommitsInfo() {
    return new PermanentCommitsInfo<>() {
      @Override
      public @NotNull CommitId getCommitId(int nodeId) {
        return myFunction.apply(nodeId);
      }

      @Override
      public long getTimestamp(int nodeId) {
        return myTimestampGetter.getTimestamp(nodeId);
      }

      @Override
      public int getNodeId(@NotNull CommitId commitId) {
        for (int id = 0; id < myLinearGraph.nodesCount(); id++) {
          if (myFunction.apply(id).equals(commitId)) {
            return id;
          }
        }
        return -1;
      }

      @Override
      public @NotNull Set<Integer> convertToNodeIds(@NotNull Collection<? extends CommitId> heads) {
        Set<Integer> result = new HashSet<>();
        for (int id = 0; id < myLinearGraph.nodesCount(); id++) {
          if (heads.contains(myFunction.apply(id))) {
            result.add(id);
          }
        }
        return result;
      }
    };
  }

  @Override
  public @NotNull LinearGraph getLinearGraph() {
    return myLinearGraph;
  }

  @Override
  public @NotNull GraphLayout getPermanentGraphLayout() {
    return myGraphLayout;
  }

  @Override
  public @NotNull Set<Integer> getBranchNodeIds() {
    return myBranchNodeIds;
  }

  private static final class CommitIdMapFunction<CommitId> implements Function<Integer, @NotNull CommitId> {
    private final List<? extends CommitId> myCommitsIdMap;

    CommitIdMapFunction(List<? extends CommitId> commitsIdMap) {
      myCommitsIdMap = commitsIdMap;
    }

    @Override
    public @NotNull CommitId apply(Integer dom) {
      return myCommitsIdMap.get(dom);
    }
  }

  private static final class IntegerCommitIdMapFunction implements Function<Integer, @NotNull Integer> {
    private final IntList myCommitsIdMap;

    IntegerCommitIdMapFunction(IntList commitsIdMap) {
      myCommitsIdMap = commitsIdMap;
    }

    @Override
    public @NotNull Integer apply(Integer dom) {
      return myCommitsIdMap.get(dom);
    }
  }
}
