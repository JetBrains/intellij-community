/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.highlighters;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

public class CurrentBranchHighlighter implements VcsLogHighlighter {
  private static final JBColor CURRENT_BRANCH_BG = new JBColor(new Color(228, 250, 255), new Color(63, 71, 73));
  private static final String HEAD = "HEAD";
  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogUi myLogUi;
  @NotNull private final Map<VirtualFile, Condition<CommitId>> myConditions = ContainerUtil.newHashMap();
  @Nullable private String mySingleFilteredBranch;

  public CurrentBranchHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi) {
    myLogData = logData;
    myLogUi = logUi;
  }

  @NotNull
  @Override
  public VcsCommitStyle getStyle(@NotNull VcsShortCommitDetails details, boolean isSelected) {
    if (isSelected || !myLogUi.isHighlighterEnabled(Factory.ID)) return VcsCommitStyle.DEFAULT;
    Condition<CommitId> condition = myConditions.get(details.getRoot());
    if (condition == null) {
      VcsLogProvider provider = myLogData.getLogProvider(details.getRoot());
      String currentBranch = provider.getCurrentBranch(details.getRoot());
      if (!HEAD.equals(mySingleFilteredBranch) && currentBranch != null && !(currentBranch.equals(mySingleFilteredBranch))) {
        condition = myLogData.getContainingBranchesGetter().getContainedInBranchCondition(currentBranch, details.getRoot());
        myConditions.put(details.getRoot(), condition);
      }
      else {
        condition = Conditions.alwaysFalse();
      }
    }
    if (condition != null && condition.value(new CommitId(details.getId(), details.getRoot()))) {
      return VcsCommitStyleFactory.background(CURRENT_BRANCH_BG);
    }
    return VcsCommitStyle.DEFAULT;
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
    mySingleFilteredBranch = VcsLogUtil.getSingleFilteredBranch(dataPack.getFilters(), dataPack.getRefs());
    myConditions.clear();
  }

  public static class Factory implements VcsLogHighlighterFactory {
    @NotNull public static final String ID = "CURRENT_BRANCH";

    @NotNull
    @Override
    public VcsLogHighlighter createHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi) {
      return new CurrentBranchHighlighter(logData, logUi);
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
