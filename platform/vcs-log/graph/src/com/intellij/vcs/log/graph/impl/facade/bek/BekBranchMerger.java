// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.impl.facade.bek;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class BekBranchMerger {
  @NotNull private final List<? extends BekBranch> myBekBranches;
  @NotNull private final BekEdgeRestrictions myEdgeRestrictions;
  @NotNull private final TimestampGetter myTimestampGetter;

  @NotNull private final List<Integer> myInverseResultList = new ArrayList<>();

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

    assert !prepareForInsertPart.isEmpty();
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
    assert prepareForInsertPart != null;
    for (int insertedNode : prepareForInsertPart) {
      myEdgeRestrictions.removeRestriction(insertedNode);
    }

    myInverseResultList.addAll(ContainerUtil.reverse(prepareForInsertPart));
    selectBranch.doneInsertPreparedPart();
  }

  @NotNull
  public List<Integer> getResult() {
    while (prepareLastPartsForBranches()) {
      step();
    }

    return ContainerUtil.reverse(myInverseResultList);
  }
}
