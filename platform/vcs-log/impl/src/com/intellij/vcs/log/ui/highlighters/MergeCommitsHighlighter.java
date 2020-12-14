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

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ui.JBColor.namedColor;

public class MergeCommitsHighlighter implements VcsLogHighlighter {
  public static final JBColor MERGE_COMMIT_FOREGROUND = namedColor("VersionControl.Log.Commit.unmatchedForeground",
                                                                   new JBColor(Gray._128, Gray._96));

  @NotNull
  @Override
  public VcsCommitStyle getStyle(int commitId, @NotNull VcsShortCommitDetails details, boolean isSelected) {
    if (isSelected) return VcsCommitStyle.DEFAULT;
    if (details.getParents().size() >= 2) return VcsCommitStyleFactory.foreground(MERGE_COMMIT_FOREGROUND);
    return VcsCommitStyle.DEFAULT;
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
  }

  public static class Factory implements VcsLogHighlighterFactory {
    @NotNull @NonNls public static final String ID = "MERGE_COMMITS";

    @NotNull
    @Override
    public VcsLogHighlighter createHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi) {
      return new MergeCommitsHighlighter();
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @NotNull
    @Override
    public String getTitle() {
      return VcsLogBundle.message("vcs.log.action.highlight.merge.commits");
    }

    @Override
    public boolean showMenuItem() {
      return true;
    }
  }
}
