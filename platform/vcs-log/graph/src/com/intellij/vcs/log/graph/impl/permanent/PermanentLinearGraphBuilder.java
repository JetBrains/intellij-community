// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.impl.permanent;

import com.intellij.util.NotNullFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.vcs.log.graph.impl.permanent.DuplicateParentFixer.fixDuplicateParentCommits;

public final class PermanentLinearGraphBuilder<CommitId> {

  @NotNull private final List<? extends GraphCommit<CommitId>> myCommits;
  @NotNull private final Flags mySimpleNodes;

  private final int myNodesCount;

  private final int @NotNull [] myNodeToEdgeIndex;
  private final int @NotNull [] myLongEdges;
  // downCommitId -> List of upNodeIndex
  @NotNull private final Map<CommitId, List<Integer>> upAdjacentNodes = new HashMap<>();

  private PermanentLinearGraphBuilder(@NotNull List<? extends GraphCommit<CommitId>> commits,
                                      @NotNull Flags simpleNodes,
                                      int longEdgesCount) {
    myCommits = commits;
    mySimpleNodes = simpleNodes;

    myNodesCount = simpleNodes.size();

    myNodeToEdgeIndex = new int[myNodesCount + 1];
    myLongEdges = new int[2 * longEdgesCount];
  }

  @NotNull
  public static <CommitId> PermanentLinearGraphBuilder<CommitId> newInstance(@NotNull List<? extends GraphCommit<CommitId>> graphCommits) {
    graphCommits = fixDuplicateParentCommits(graphCommits);
    Flags simpleNodes = new BitSetFlags(graphCommits.size());

    int longEdgesCount = 0;

    for (int nodeIndex = 0; nodeIndex < graphCommits.size(); nodeIndex++) {
      GraphCommit<CommitId> commit = graphCommits.get(nodeIndex);

      CommitId nextCommitHashIndex = nextCommitHashIndex(graphCommits, nodeIndex);

      List<CommitId> parents = commit.getParents();
      if (parents.size() == 1 && parents.get(0).equals(nextCommitHashIndex)) {
        simpleNodes.set(nodeIndex, true);
      }
      else {
        longEdgesCount += parents.size();
      }
    }

    return new PermanentLinearGraphBuilder<>(graphCommits, simpleNodes, longEdgesCount);
  }

  @Nullable
  private static <CommitId> CommitId nextCommitHashIndex(List<? extends GraphCommit<CommitId>> commits, int nodeIndex) {
    if (nodeIndex < commits.size() - 1) return commits.get(nodeIndex + 1).getId();
    return null;
  }

  private void addUnderdoneEdge(int upNodeIndex, CommitId downCommitId) {
    List<Integer> upNodes = upAdjacentNodes.get(downCommitId);
    if (upNodes == null) {
      upNodes = new SmartList<>();
      upAdjacentNodes.put(downCommitId, upNodes);
    }
    upNodes.add(upNodeIndex);
  }

  private void fixUnderdoneEdge(int upNodeIndex, int downNodeIndex, CommitId downCommitId) {
    int end = myNodeToEdgeIndex[upNodeIndex + 1];

    GraphCommit<CommitId> upCommit = myCommits.get(upNodeIndex);
    List<CommitId> parentHashIndices = upCommit.getParents();

    for (int i = 0; i < parentHashIndices.size(); i++) {
      if (parentHashIndices.get(i).equals(downCommitId)) {
        int offset = parentHashIndices.size() - i;
        int edgeIndex = end - offset;

        if (myLongEdges[edgeIndex] == -1) {
          myLongEdges[edgeIndex] = downNodeIndex;
          return;
        }
        else {
          throw new IllegalStateException("Edge was set early!. Up node: " + upNodeIndex + ", down node: " + downNodeIndex);
        }
      }
    }
    throw new IllegalStateException("Not found underdone edges for node: " + upNodeIndex + ". Adjacent down node: " + downNodeIndex);
  }

  private void doStep(int nodeIndex) {
    GraphCommit<CommitId> commit = myCommits.get(nodeIndex);

    List<Integer> upNodes = upAdjacentNodes.remove(commit.getId());
    if (upNodes == null) upNodes = Collections.emptyList();

    int edgeIndex = myNodeToEdgeIndex[nodeIndex];
    for (Integer upNodeIndex : upNodes) {
      fixUnderdoneEdge(upNodeIndex, nodeIndex, commit.getId());
      myLongEdges[edgeIndex] = upNodeIndex;
      edgeIndex++;
    }

    // down nodes
    if (!mySimpleNodes.get(nodeIndex)) {
      for (CommitId downCommitId : commit.getParents()) {
        addUnderdoneEdge(nodeIndex, downCommitId);
        myLongEdges[edgeIndex] = -1;
        edgeIndex++;
      }
    }

    myNodeToEdgeIndex[nodeIndex + 1] = edgeIndex;
  }

  private void fixUnderdoneEdgeForNotLoadCommit(int upNodeIndex, int notLoadId) {
    for (int edgeIndex = myNodeToEdgeIndex[upNodeIndex]; edgeIndex < myNodeToEdgeIndex[upNodeIndex + 1]; edgeIndex++) {
      if (myLongEdges[edgeIndex] == -1) {
        myLongEdges[edgeIndex] = notLoadId;
        return;
      }
    }
    throw new IllegalStateException("Not found underdone edge to not load commit for node: " + upNodeIndex);
  }

  private void fixUnderdoneEdges(@NotNull NotNullFunction<? super CommitId, Integer> notLoadedCommitToId) {
    List<CommitId> commitIds = new ArrayList<>(upAdjacentNodes.keySet());
    ContainerUtil.sort(commitIds, Comparator.comparingInt(o -> Collections.min(upAdjacentNodes.get(o))));
    for (CommitId notLoadCommit : commitIds) {
      int notLoadId = notLoadedCommitToId.fun(notLoadCommit);
      for (int upNodeIndex : upAdjacentNodes.get(notLoadCommit)) {
        fixUnderdoneEdgeForNotLoadCommit(upNodeIndex, notLoadId);
      }
    }
  }

  // id's must be less that -2
  @NotNull
  public PermanentLinearGraphImpl build(@NotNull NotNullFunction<? super CommitId, Integer> notLoadedCommitToId) {
    for (int nodeIndex = 0; nodeIndex < myNodesCount; nodeIndex++) {
      doStep(nodeIndex);
    }

    fixUnderdoneEdges(notLoadedCommitToId);

    return new PermanentLinearGraphImpl(mySimpleNodes, myNodeToEdgeIndex, myLongEdges);
  }

  @NotNull
  public PermanentLinearGraphImpl build() {
    return build(dom -> Integer.MIN_VALUE);
  }
}
