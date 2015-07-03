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

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.vcs.log.VcsCommitStyleFactory;
import com.intellij.vcs.log.VcsLogFilterUi;
import com.intellij.vcs.log.VcsLogHighlighter;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import org.jetbrains.annotations.NotNull;

public class MergeCommitsHighlighter implements VcsLogHighlighter {
  public static final JBColor MERGE_COMMIT_FOREGROUND = new JBColor(Gray._128, Gray._96);
  @NotNull private final VcsLogUiProperties myUiProperties;
  @NotNull private final VcsLogDataHolder myDataHolder;

  public MergeCommitsHighlighter(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUiProperties uiProperties) {
    myDataHolder = logDataHolder;
    myUiProperties = uiProperties;
  }

  @NotNull
  @Override
  public VcsCommitStyle getStyle(int commitIndex, boolean isSelected) {
    if (isSelected || !myUiProperties.isHighlighterEnabled(Factory.ID)) return VcsCommitStyle.DEFAULT;
    VcsShortCommitDetails details = myDataHolder.getMiniDetailsGetter().getCommitDataIfAvailable(commitIndex);
    if (details != null && !(details instanceof LoadingDetails)) {
      if (details.getParents().size() >= 2) return VcsCommitStyleFactory.foreground(MERGE_COMMIT_FOREGROUND);
    }
    return VcsCommitStyle.DEFAULT;
  }

  public static class Factory implements VcsLogHighlighterFactory {
    @NotNull
    private static final String ID = "MERGE_COMMITS";

    @NotNull
    @Override
    public VcsLogHighlighter createHighlighter(@NotNull VcsLogDataHolder logDataHolder,
                                               @NotNull VcsLogUiProperties uiProperties,
                                               @NotNull VcsLogFilterUi filterUi) {
      return new MergeCommitsHighlighter(logDataHolder, uiProperties);
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @NotNull
    @Override
    public String getTitle() {
      return "Merge Commits";
    }
  }
}
