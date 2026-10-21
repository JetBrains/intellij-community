// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort.bek;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

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

  /** The sorted order, filled from the end: {@link #myResultStart} is the index of the lowest placed node. */
  private final int @NotNull [] myResult;
  private int myResultStart;

  BekBranchMerger(@NotNull List<? extends BekBranch> bekBranches,
                  @NotNull BekEdgeRestrictions edgeRestrictions,
                  @NotNull TimestampGetter timestampGetter,
                  int nodesCount) {
    myBekBranches = bekBranches;
    myEdgeRestrictions = edgeRestrictions;
    myTimestampGetter = timestampGetter;

    myNodeToBranch = new int[nodesCount];
    for (int branchIndex = 0; branchIndex < bekBranches.size(); branchIndex++) {
      IntList nodeIndexes = bekBranches.get(branchIndex).getNodeIndexes();
      for (int i = 0; i < nodeIndexes.size(); i++) {
        myNodeToBranch[nodeIndexes.getInt(i)] = branchIndex;
      }
    }

    myQueuedTimestamps = new long[bekBranches.size()];
    myQueue = new PriorityQueue<>(new BranchOrderComparator());

    myResult = new int[nodesCount];
    myResultStart = nodesCount;
  }

  private void prepareAndEnqueue(int branchIndex) {
    BekBranch bekBranch = myBekBranches.get(branchIndex);
    if (bekBranch.isDone() || bekBranch.getPrepareForInsertPart() != null) return;

    bekBranch.updatePrepareForInsertPart(myTimestampGetter, myEdgeRestrictions);
    IntList prepareForInsertPart = bekBranch.getPrepareForInsertPart();
    if (prepareForInsertPart == null) return;

    LOG.assertTrue(!prepareForInsertPart.isEmpty());
    myQueuedTimestamps[branchIndex] = myTimestampGetter.getTimestamp(prepareForInsertPart.getInt(0));
    myQueue.add(branchIndex);
  }

  private void step() {
    int branchIndex = myQueue.remove();
    BekBranch selectBranch = myBekBranches.get(branchIndex);

    IntList prepareForInsertPart = selectBranch.getPrepareForInsertPart();
    LOG.assertTrue(prepareForInsertPart != null);

    myReleasedNodes.clear();
    myResultStart -= prepareForInsertPart.size();
    for (int i = 0; i < prepareForInsertPart.size(); i++) {
      int insertedNode = prepareForInsertPart.getInt(i);
      myResult[myResultStart + i] = insertedNode;
      myEdgeRestrictions.removeRestriction(insertedNode, node -> myReleasedNodes.add(node));
    }
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

  /**
   * @return the node indexes in the sorted order, from the top to the bottom
   */
  public int @NotNull [] getResult() {
    for (int branchIndex = 0; branchIndex < myBekBranches.size(); branchIndex++) {
      prepareAndEnqueue(branchIndex);
    }
    while (!myQueue.isEmpty()) {
      step();
    }
    LOG.assertTrue(myResultStart == 0, "Unplaced nodes: " + myResultStart);
    return myResult;
  }

  private class BranchOrderComparator implements Comparator<Integer> {
    @Override
    public int compare(@NotNull Integer branch1, @NotNull Integer branch2) {
      int byTimestamp = Long.compare(myQueuedTimestamps[branch1], myQueuedTimestamps[branch2]);
      if (byTimestamp != 0) return byTimestamp;
      return Integer.compare(branch1, branch2);
    }
  }
}
