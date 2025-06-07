// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.vcs.log.graph.impl.permanent.PermanentLinearGraphBuilder;
import com.intellij.vcs.log.graph.impl.permanent.PermanentLinearGraphImpl;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.asLiteLinearGraph;

@ApiStatus.Internal
public final class SimpleGraphInfo<CommitId> implements PermanentGraphInfo<CommitId> {
  private final @NotNull LinearGraph myLinearGraph;
  private final @NotNull GraphLayout myGraphLayout;
  private final @NotNull Set<Integer> myBranchNodeIds;

  private final @NotNull RowsMapping<CommitId> myRowsMapping;

  private SimpleGraphInfo(@NotNull LinearGraph linearGraph,
                          @NotNull GraphLayout graphLayout,
                          @NotNull Set<Integer> branchNodeIds,
                          @NotNull RowsMapping<CommitId> rowsMapping) {
    myLinearGraph = linearGraph;
    myGraphLayout = graphLayout;
    myBranchNodeIds = branchNodeIds;
    myRowsMapping = rowsMapping;
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

    RowsMapping<CommitId> rowsMapping = new RowsMapping<>(end - start, permanentCommitsInfo.getCommitId(0) instanceof Integer);
    List<GraphCommit<CommitId>> graphCommits = new ArrayList<>(end - start);
    for (int row = start; row < end; row++) {
      int nodeId = linearGraph.getNodeId(row);
      CommitId commit = permanentCommitsInfo.getCommitId(nodeId);
      List<CommitId> parents = new SmartList<>();
      parents.addAll(ContainerUtil.mapNotNull(asLiteLinearGraph(linearGraph).getNodes(row, LiteLinearGraph.NodeFilter.DOWN),
                                              row1 -> {
                                                if (row1 < start || row1 >= end) return null;
                                                return permanentCommitsInfo.getCommitId(linearGraph.getNodeId(row1));
                                              }));
      long timestamp = permanentCommitsInfo.getTimestamp(nodeId);
      rowsMapping.add(commit, timestamp);
      graphCommits.add(GraphCommitImpl.createCommit(commit, parents, timestamp));
    }

    PermanentLinearGraphImpl newLinearGraph = PermanentLinearGraphBuilder.newInstance(graphCommits).build();

    int[] layoutIndexes = new int[end - start];
    IntList headNodeIndexes = new IntArrayList();

    Object2IntMap<CommitId> commitIdToInteger = reverseCommitIdMap(permanentCommitsInfo, permanentGraphSize);
    for (int row = start; row < end; row++) {
      CommitId commitId = graphCommits.get(row - start).getId();
      int layoutIndex = oldLayout.getLayoutIndex(commitIdToInteger.getInt(commitId));
      layoutIndexes[row - start] = layoutIndex;
      if (asLiteLinearGraph(newLinearGraph).getNodes(row - start, LiteLinearGraph.NodeFilter.UP).isEmpty()) {
        headNodeIndexes.add(row - start);
      }
    }

    ContainerUtil.sort(headNodeIndexes, Comparator.comparingInt(o -> layoutIndexes[o]));
    GraphLayoutImpl newLayout = new GraphLayoutImpl(layoutIndexes, headNodeIndexes);

    return new SimpleGraphInfo<>(newLinearGraph, newLayout, LinearGraphUtils.convertIdsToNodeIndexes(linearGraph, branchNodeIds), rowsMapping);
  }

  private static @NotNull <CommitId> Object2IntMap<CommitId> reverseCommitIdMap(@NotNull PermanentCommitsInfo<CommitId> permanentCommitsInfo,
                                                                                int size) {
    Object2IntMap<CommitId> result = new Object2IntOpenHashMap<>(size);
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
        return myRowsMapping.getCommitId(nodeId);
      }

      @Override
      public long getTimestamp(int nodeId) {
        return myRowsMapping.getTimestamp(nodeId);
      }

      @Override
      public int getNodeId(@NotNull CommitId commitId) {
        for (int id = 0; id < myLinearGraph.nodesCount(); id++) {
          if (myRowsMapping.getCommitId(id).equals(commitId)) {
            return id;
          }
        }
        return -1;
      }

      @Override
      public @NotNull Set<Integer> convertToNodeIds(@NotNull Collection<? extends CommitId> commitIds) {
        Set<Integer> result = new HashSet<>();
        for (int id = 0; id < myLinearGraph.nodesCount(); id++) {
          if (commitIds.contains(myRowsMapping.getCommitId(id))) {
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
}
