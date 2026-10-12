// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort.bek;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.impl.facade.sort.SortChecker;
import com.intellij.vcs.log.graph.impl.facade.sort.SortIndexMap;
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutImpl;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class BekSorter {
  private static final Logger LOG = Logger.getInstance(BekSorter.class);

  public static @NotNull SortIndexMap createBekMap(@NotNull LinearGraph permanentGraph,
                                                   @NotNull GraphLayoutImpl graphLayout,
                                                   @NotNull TimestampGetter timestampGetter) {
    long start = System.nanoTime();
    BekBranchCreator bekBranchCreator = new BekBranchCreator(permanentGraph, graphLayout);
    Pair<List<BekBranch>, BekEdgeRestrictions> branches = bekBranchCreator.getResult();
    long branchesCreated = System.nanoTime();

    BekBranchMerger bekBranchMerger = new BekBranchMerger(branches.first, branches.second, timestampGetter, permanentGraph.nodesCount());
    int[] result = bekBranchMerger.getResult();
    long branchesMerged = System.nanoTime();

    LOG.assertTrue(result.length == permanentGraph.nodesCount());
    SortIndexMap sortIndexMap = new SortIndexMap(result);
    SortChecker.checkSortIndexMap(permanentGraph, sortIndexMap);
    long checked = System.nanoTime();

    if (LOG.isDebugEnabled()) {
      LOG.debug("Bek sort of " + permanentGraph.nodesCount() + " nodes in " + branches.first.size() + " branches in " + TimeUnit.NANOSECONDS.toMillis(checked - start) + " ms: " +
                "branches created in " + TimeUnit.NANOSECONDS.toMillis(branchesCreated - start) + " ms, " +
                "merged in " + TimeUnit.NANOSECONDS.toMillis(branchesMerged - branchesCreated) + " ms, " +
                "checked in " + TimeUnit.NANOSECONDS.toMillis(checked - branchesMerged) + " ms, " +
                "order hash " + Long.toHexString(orderHash(sortIndexMap)));
    }
    return sortIndexMap;
  }

  /**
   * @return the FNV-1a hash of the sorted order, to compare two runs on the same graph
   */
  private static long orderHash(@NotNull SortIndexMap sortIndexMap) {
    long hash = 0xcbf29ce484222325L;
    for (int i = 0; i < sortIndexMap.size(); i++) {
      hash = (hash ^ sortIndexMap.getUsualIndex(i)) * 0x100000001b3L;
    }
    return hash;
  }
}
