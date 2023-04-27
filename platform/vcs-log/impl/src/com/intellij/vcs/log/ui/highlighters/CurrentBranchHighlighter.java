// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.highlighters;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static com.intellij.ui.JBColor.namedColor;

public class CurrentBranchHighlighter implements VcsLogHighlighter {
  private static final JBColor CURRENT_BRANCH_BG = namedColor("VersionControl.Log.Commit.currentBranchBackground",
                                                              new JBColor(new Color(228, 250, 255), new Color(63, 71, 73)));
  private final @NotNull VcsLogData myLogData;
  private final @NotNull Map<VirtualFile, Boolean> myIsHighlighted = new HashMap<>();

  public CurrentBranchHighlighter(@NotNull VcsLogData logData) {
    myLogData = logData;
  }

  @Override
  public @NotNull VcsCommitStyle getStyle(int commitId, @NotNull VcsShortCommitDetails details, int column, boolean isSelected) {
    if (isSelected) return VcsCommitStyle.DEFAULT;
    if (!myIsHighlighted.getOrDefault(details.getRoot(), false)) return VcsCommitStyle.DEFAULT;

    Predicate<Integer> condition = myLogData.getContainingBranchesGetter().getContainedInCurrentBranchCondition(details.getRoot());
    if (condition.test(commitId)) {
      return VcsCommitStyleFactory.background(CURRENT_BRANCH_BG);
    }
    return VcsCommitStyle.DEFAULT;
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
    String singleFilteredBranch = VcsLogUtil.getSingleFilteredBranch(dataPack.getFilters(), dataPack.getRefs());
    myIsHighlighted.clear();
    boolean isHeadFilter = VcsLogUtil.HEAD.equals(singleFilteredBranch);
    for (VirtualFile root : dataPack.getLogProviders().keySet()) {
      String currentBranch = dataPack.getLogProviders().get(root).getCurrentBranch(root);
      myIsHighlighted.put(root, !isHeadFilter && currentBranch != null && !(currentBranch.equals(singleFilteredBranch)));
    }
  }

  public static class Factory implements VcsLogHighlighterFactory {
    public static final @NonNls @NotNull String ID = "CURRENT_BRANCH";

    @Override
    public @NotNull VcsLogHighlighter createHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi) {
      return new CurrentBranchHighlighter(logData);
    }

    @Override
    public @NotNull String getId() {
      return ID;
    }

    @Override
    public @NotNull String getTitle() {
      return VcsLogBundle.message("vcs.log.action.highlight.current.branch");
    }

    @Override
    public boolean showMenuItem() {
      return true;
    }
  }
}
