// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort.bek;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.impl.facade.sort.SortIndexMap;
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutImpl;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class BekSorter {
  private final static Logger LOG = Logger.getInstance(BekSorter.class);

  @NotNull
  public static SortIndexMap createBekMap(@NotNull LinearGraph permanentGraph,
                                          @NotNull GraphLayoutImpl graphLayout,
                                          @NotNull TimestampGetter timestampGetter) {
    BekBranchCreator bekBranchCreator = new BekBranchCreator(permanentGraph, graphLayout);
    Pair<List<BekBranch>, BekEdgeRestrictions> branches = bekBranchCreator.getResult();

    BekBranchMerger bekBranchMerger = new BekBranchMerger(branches.first, branches.second, timestampGetter);
    List<Integer> result = bekBranchMerger.getResult();

    LOG.assertTrue(result.size() == permanentGraph.nodesCount());
    return new SortIndexMap(result);
  }
}
