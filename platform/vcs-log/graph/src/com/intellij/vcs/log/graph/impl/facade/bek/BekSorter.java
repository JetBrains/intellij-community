// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.bek;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutImpl;
import com.intellij.vcs.log.graph.utils.IntList;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import com.intellij.vcs.log.graph.utils.impl.CompressedIntList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class BekSorter {
  private final static Logger LOG = Logger.getInstance(BekSorter.class);

  @NotNull
  public static BekIntMap createBekMap(@NotNull LinearGraph permanentGraph,
                                       @NotNull GraphLayoutImpl graphLayout,
                                       @NotNull TimestampGetter timestampGetter) {
    BekBranchCreator bekBranchCreator = new BekBranchCreator(permanentGraph, graphLayout);
    Pair<List<BekBranch>, BekEdgeRestrictions> branches = bekBranchCreator.getResult();

    BekBranchMerger bekBranchMerger = new BekBranchMerger(branches.first, branches.second, timestampGetter);
    List<Integer> result = bekBranchMerger.getResult();

    LOG.assertTrue(result.size() == permanentGraph.nodesCount());
    return createBekIntMap(result);
  }

  private static BekIntMap createBekIntMap(final List<Integer> result) {

    final int[] reverseMap = new int[result.size()];
    for (int i = 0; i < result.size(); i++) {
      reverseMap[result.get(i)] = i;
    }

    final IntList compressedBekMap = CompressedIntList.newInstance(new IntList() {
      @Override
      public int size() {
        return result.size();
      }

      @Override
      public int get(int index) {
        return result.get(index);
      }
    }, CompressedIntList.DEFAULT_BLOCK_SIZE);

    final IntList compressedReverseMap = CompressedIntList.newInstance(reverseMap);
    return new BekIntMap() {
      @Override
      public int size() {
        return compressedBekMap.size();
      }

      @Override
      public int getBekIndex(int usualIndex) {
        return compressedReverseMap.get(usualIndex);
      }

      @Override
      public int getUsualIndex(int bekIndex) {
        return compressedBekMap.get(bekIndex);
      }
    };
  }
}
