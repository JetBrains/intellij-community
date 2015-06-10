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
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class CurrentBranchHighlighter implements VcsLogHighlighter {
  private static final JBColor CURRENT_BRANCH_BG = new JBColor(new Color(228, 250, 255), new Color(63, 71, 73));
  @NotNull private final VcsLogUiProperties myUiProperties;
  @NotNull private final VcsLogDataHolder myDataHolder;

  public CurrentBranchHighlighter(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUiProperties uiProperties) {
    myDataHolder = logDataHolder;
    myUiProperties = uiProperties;
  }

  @NotNull
  @Override
  public VcsCommitStyle getStyle(int commitIndex, boolean isSelected) {
    if (isSelected || !myUiProperties.isHighlighterEnabled(Factory.ID)) return VcsCommitStyle.DEFAULT;
    VcsShortCommitDetails details = myDataHolder.getMiniDetailsGetter().getCommitDataIfAvailable(commitIndex);
    if (details != null && !(details instanceof LoadingDetails)) {
      VcsLogProvider provider = myDataHolder.getLogProvider(details.getRoot());
      String currentBranch = provider.getCurrentBranch(details.getRoot());
      if (currentBranch != null) {
        Condition<Hash> condition = myDataHolder.getContainingBranchesGetter().getContainedInBranchCondition(currentBranch, details.getRoot());
        if (condition.value(details.getId())) {
          return VcsCommitStyleFactory.background(CURRENT_BRANCH_BG);
        }
      }
    }
    return VcsCommitStyle.DEFAULT;
  }

  public static class Factory implements VcsLogHighlighterFactory {
    @NotNull
    private static final String ID = "CURRENT_BRANCH";

    @NotNull
    @Override
    public VcsLogHighlighter createHighlighter(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUiProperties uiProperties) {
      return new CurrentBranchHighlighter(logDataHolder, uiProperties);
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
  }
}
