// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CurrentBranchConditionCache {
  private static final Logger LOG = Logger.getInstance(CurrentBranchConditionCache.class);

  @NotNull private final VcsLogData myLogData;
  @NotNull private Map<VirtualFile, Condition<Integer>> myConditions = new HashMap<>();

  public CurrentBranchConditionCache(@NotNull VcsLogData data) {
    myLogData = data;
  }

  @NotNull
  public Condition<Integer> getContainedInCurrentBranchCondition(@NotNull VirtualFile root) {
    LOG.assertTrue(EventQueue.isDispatchThread());

    Condition<Integer> condition = myConditions.get(root);
    if (condition == null) {
      condition = doGetContainedInCurrentBranchCondition(root);
      myConditions.put(root, condition);
    }
    return condition;
  }

  @NotNull
  private Condition<Integer> doGetContainedInCurrentBranchCondition(@NotNull VirtualFile root) {
    DataPack dataPack = myLogData.getDataPack();
    if (dataPack == DataPack.EMPTY) return Conditions.alwaysFalse();

    String branchName = myLogData.getLogProvider(root).getCurrentBranch(root);
    if (branchName == null) return Conditions.alwaysFalse();

    VcsRef branchRef = VcsLogUtil.findBranch(dataPack.getRefsModel(), root, branchName);
    if (branchRef == null) return Conditions.alwaysFalse();

    int branchIndex = myLogData.getCommitIndex(branchRef.getCommitHash(), branchRef.getRoot());
    return dataPack.getPermanentGraph().getContainedInBranchCondition(Collections.singleton(branchIndex));
  }

  public void clear() {
    myConditions = new HashMap<>();
  }
}
