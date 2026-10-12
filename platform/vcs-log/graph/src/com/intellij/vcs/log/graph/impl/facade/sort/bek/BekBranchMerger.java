// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort.bek;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Merges the branches of {@link BekBranchCreator} into one topological order.
 * <p>
 * The merger works from the bottom of the graph to the top. Each branch has a current node: its lowest node that is
 * not placed yet. A branch prepares a part, a run of nodes that ends at the current node, cut at a merge commit, at a
 * gap in the branch, at a long time gap, or at the block size limit (see {@link BekBranch#updatePrepareForInsertPart}).
 * Each step takes the branch whose prepared part has the smallest timestamp of its top node, places the part below
 * the nodes placed so far, and lets the branch prepare its next part.
 * <p>
 * An edge restriction (up node, down node) comes from the creator: the down node belongs to a branch with a lower
 * layout index and must be placed before the up node. A branch whose current node has a restriction cannot prepare a
 * part and waits outside the queue. When a node is placed, {@link BekEdgeRestrictions} reports the up nodes that lose
 * their last restriction, and the merger prepares and queues the owning branch if the released node is its current
 * node.
 * <p>
 * The queue is keyed by the part timestamp with the branch index as the tie-break, so it selects the same branch as a
 * linear scan over the branch list. The released branches are prepared after all removals of the step, so a part sees
 * the same restrictions as a part prepared at the start of the next step.
 */
class BekBranchMerger {
  private static final Logger LOG = Logger.getInstance(BekBranchMerger.class);
  private final @NotNull List<? extends BekBranch> myBekBranches;
  private final @NotNull BekEdgeRestrictions myEdgeRestrictions;
  private final @NotNull TimestampGetter myTimestampGetter;

  /** The branch that owns each node. */
  private final int @NotNull [] myNodeToBranch;
  /** The timestamp of the top node of the prepared part, per branch. Valid while the branch is in the queue. */
  private final long @NotNull [] myQueuedTimestamps;
  /** The indexes of the branches with a prepared part, ordered by {@link #myQueuedTimestamps} and then by index. */
  private final @NotNull PriorityQueue<Integer> myQueue;
  /** The up nodes released by the removals of the current step. */
  private final @NotNull IntList myReleasedNodes = new IntArrayList();

  private final @NotNull List<Integer> myInverseResultList = new ArrayList<>();

  BekBranchMerger(@NotNull List<? extends BekBranch> bekBranches,
                  @NotNull BekEdgeRestrictions edgeRestrictions,
                  @NotNull TimestampGetter timestampGetter,
                  int nodesCount) {
    myBekBranches = bekBranches;
    myEdgeRestrictions = edgeRestrictions;
    myTimestampGetter = timestampGetter;

    myNodeToBranch = new int[nodesCount];
    for (int branchIndex = 0; branchIndex < bekBranches.size(); branchIndex++) {
      for (int nodeIndex : bekBranches.get(branchIndex).getNodeIndexes()) {
        myNodeToBranch[nodeIndex] = branchIndex;
      }
    }

    long[] queuedTimestamps = new long[bekBranches.size()];
    myQueuedTimestamps = queuedTimestamps;
    myQueue = new PriorityQueue<>(Comparator.<Integer>comparingLong(branch -> queuedTimestamps[branch]).thenComparing(Comparator.naturalOrder()));
  }

  private void prepareAndEnqueue(int branchIndex) {
    BekBranch bekBranch = myBekBranches.get(branchIndex);
    if (bekBranch.isDone() || bekBranch.getPrepareForInsertPart() != null) return;

    bekBranch.updatePrepareForInsertPart(myTimestampGetter, myEdgeRestrictions);
    List<Integer> prepareForInsertPart = bekBranch.getPrepareForInsertPart();
    if (prepareForInsertPart == null) return;

    LOG.assertTrue(!prepareForInsertPart.isEmpty());
    myQueuedTimestamps[branchIndex] = myTimestampGetter.getTimestamp(prepareForInsertPart.get(0));
    myQueue.add(branchIndex);
  }

  private void step() {
    int branchIndex = myQueue.remove();
    BekBranch selectBranch = myBekBranches.get(branchIndex);

    List<Integer> prepareForInsertPart = selectBranch.getPrepareForInsertPart();
    LOG.assertTrue(prepareForInsertPart != null);

    myReleasedNodes.clear();
    for (int insertedNode : prepareForInsertPart) {
      myEdgeRestrictions.removeRestriction(insertedNode, node -> myReleasedNodes.add(node));
    }

    myInverseResultList.addAll(ContainerUtil.reverse(prepareForInsertPart));
    selectBranch.doneInsertPreparedPart();

    for (int i = 0; i < myReleasedNodes.size(); i++) {
      int releasedNode = myReleasedNodes.getInt(i);
      int releasedBranchIndex = myNodeToBranch[releasedNode];
      BekBranch releasedBranch = myBekBranches.get(releasedBranchIndex);
      if (!releasedBranch.isDone() && releasedBranch.getCurrentNode() == releasedNode) {
        prepareAndEnqueue(releasedBranchIndex);
      }
    }
    prepareAndEnqueue(branchIndex);
  }

  public @NotNull List<Integer> getResult() {
    for (int branchIndex = 0; branchIndex < myBekBranches.size(); branchIndex++) {
      prepareAndEnqueue(branchIndex);
    }
    while (!myQueue.isEmpty()) {
      step();
    }

    return ContainerUtil.reverse(myInverseResultList);
  }
}
