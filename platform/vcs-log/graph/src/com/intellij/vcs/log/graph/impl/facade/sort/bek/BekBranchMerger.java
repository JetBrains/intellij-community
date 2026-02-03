// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort.bek;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class BekBranchMerger {
  private static final Logger LOG = Logger.getInstance(BekBranchMerger.class);
  private final @NotNull List<? extends BekBranch> myBekBranches;
  private final @NotNull BekEdgeRestrictions myEdgeRestrictions;
  private final @NotNull TimestampGetter myTimestampGetter;

  private final @NotNull List<Integer> myInverseResultList = new ArrayList<>();

  BekBranchMerger(@NotNull List<? extends BekBranch> bekBranches,
                  @NotNull BekEdgeRestrictions edgeRestrictions,
                  @NotNull TimestampGetter timestampGetter) {
    myBekBranches = bekBranches;
    myEdgeRestrictions = edgeRestrictions;
    myTimestampGetter = timestampGetter;
  }

  // return true, if exist some undone branch
  private boolean prepareLastPartsForBranches() {
    boolean hasUndoneBranches = false;
    for (BekBranch bekBranch : myBekBranches) {
      if (!bekBranch.isDone()) {
        hasUndoneBranches = true;
        if (bekBranch.getPrepareForInsertPart() == null) {
          bekBranch.updatePrepareForInsertPart(myTimestampGetter, myEdgeRestrictions);
        }
      }
    }
    return hasUndoneBranches;
  }

  private long getBranchLastPartTimestamp(BekBranch bekBranch) {
    List<Integer> prepareForInsertPart = bekBranch.getPrepareForInsertPart();
    if (prepareForInsertPart == null) return Long.MAX_VALUE;

    LOG.assertTrue(!prepareForInsertPart.isEmpty());
    int nodeIndex = prepareForInsertPart.get(0);
    return myTimestampGetter.getTimestamp(nodeIndex);
  }

  private void step() {
    BekBranch selectBranch = myBekBranches.get(0);
    for (BekBranch bekBranch : myBekBranches) {
      if (getBranchLastPartTimestamp(selectBranch) > getBranchLastPartTimestamp(bekBranch)) {
        selectBranch = bekBranch;
      }
    }

    List<Integer> prepareForInsertPart = selectBranch.getPrepareForInsertPart();
    LOG.assertTrue(prepareForInsertPart != null);
    for (int insertedNode : prepareForInsertPart) {
      myEdgeRestrictions.removeRestriction(insertedNode);
    }

    myInverseResultList.addAll(ContainerUtil.reverse(prepareForInsertPart));
    selectBranch.doneInsertPreparedPart();
  }

  public @NotNull List<Integer> getResult() {
    while (prepareLastPartsForBranches()) {
      step();
    }

    return ContainerUtil.reverse(myInverseResultList);
  }
}
