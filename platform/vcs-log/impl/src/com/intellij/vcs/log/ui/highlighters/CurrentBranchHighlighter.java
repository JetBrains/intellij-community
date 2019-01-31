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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;

import static com.intellij.ui.JBColor.namedColor;

public class CurrentBranchHighlighter implements VcsLogHighlighter {
  private static final JBColor CURRENT_BRANCH_BG = namedColor("VersionControl.Log.Commit.currentBranchBackground",
                                                              new JBColor(new Color(228, 250, 255), new Color(63, 71, 73)));
  private static final String HEAD = "HEAD";
  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogUi myLogUi;
  @NotNull private final Map<VirtualFile, Boolean> myIsHighlighted = ContainerUtil.newHashMap();

  public CurrentBranchHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi) {
    myLogData = logData;
    myLogUi = logUi;
  }

  @NotNull
  @Override
  public VcsCommitStyle getStyle(int commitId, @NotNull VcsShortCommitDetails details, boolean isSelected) {
    if (isSelected || !myLogUi.isHighlighterEnabled(Factory.ID)) return VcsCommitStyle.DEFAULT;
    if (!myIsHighlighted.getOrDefault(details.getRoot(), false)) return VcsCommitStyle.DEFAULT;

    Condition<Integer> condition = myLogData.getContainingBranchesGetter().getContainedInCurrentBranchCondition(details.getRoot());
    if (condition.value(commitId)) {
      return VcsCommitStyleFactory.background(CURRENT_BRANCH_BG);
    }
    return VcsCommitStyle.DEFAULT;
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
    String singleFilteredBranch = VcsLogUtil.getSingleFilteredBranch(dataPack.getFilters(), dataPack.getRefs());
    myIsHighlighted.clear();
    boolean isHeadFilter = HEAD.equals(singleFilteredBranch);
    for (VirtualFile root : dataPack.getLogProviders().keySet()) {
      String currentBranch = dataPack.getLogProviders().get(root).getCurrentBranch(root);
      myIsHighlighted.put(root, !isHeadFilter && currentBranch != null && !(currentBranch.equals(singleFilteredBranch)));
    }
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
