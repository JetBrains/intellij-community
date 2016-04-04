/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui;

import com.intellij.openapi.util.Condition;
import com.intellij.ui.JBColor;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.impl.VcsLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class CurrentBranchHighlighter implements VcsLogHighlighter {
  private static final JBColor CURRENT_BRANCH_BG = new JBColor(new Color(228, 250, 255), new Color(63, 71, 73));
  @NotNull private final VcsLogDataHolder myDataHolder;
  @NotNull private final VcsLogUi myLogUi;
  @Nullable private String mySingleFilteredBranch;

  public CurrentBranchHighlighter(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUi logUi) {
    myDataHolder = logDataHolder;
    myLogUi = logUi;
  }

  @NotNull
  @Override
  public VcsCommitStyle getStyle(@NotNull VcsShortCommitDetails details, boolean isSelected) {
    if (isSelected || !myLogUi.isHighlighterEnabled(Factory.ID)) return VcsCommitStyle.DEFAULT;
    VcsLogProvider provider = myDataHolder.getLogProvider(details.getRoot());
    String currentBranch = provider.getCurrentBranch(details.getRoot());
    if (currentBranch != null && !(currentBranch.equals(mySingleFilteredBranch))) {
      Condition<CommitId> condition =
        myDataHolder.getContainingBranchesGetter().getContainedInBranchCondition(currentBranch, details.getRoot());
      if (condition.value(new CommitId(details.getId(), details.getRoot()))) {
        return VcsCommitStyleFactory.background(CURRENT_BRANCH_BG);
      }
    }
    return VcsCommitStyle.DEFAULT;
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
    VcsLogBranchFilter branchFilter = dataPack.getFilters().getBranchFilter();
    mySingleFilteredBranch = branchFilter == null ? null : VcsLogUtil.getSingleFilteredBranch(branchFilter, dataPack.getRefs());
  }

  public static class Factory implements VcsLogHighlighterFactory {
    @NotNull private static final String ID = "CURRENT_BRANCH";

    @NotNull
    @Override
    public VcsLogHighlighter createHighlighter(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUi logUi) {
      return new CurrentBranchHighlighter(logDataHolder, logUi);
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @NotNull
    @Override
    public String getTitle() {
      return "Current Branch";
    }

    @Override
    public boolean showMenuItem() {
      return true;
    }
  }
}
