// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @NotNull VcsCommitStyle getStyle(int commitId, @NotNull VcsShortCommitDetails details, int column, boolean isSelected) {
    if (isSelected) return VcsCommitStyle.DEFAULT;
    if (details.getParents().size() >= 2) return VcsCommitStyleFactory.foreground(MERGE_COMMIT_FOREGROUND);
    return VcsCommitStyle.DEFAULT;
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
  }

  public static class Factory implements VcsLogHighlighterFactory {
    public static final @NotNull @NonNls String ID = "MERGE_COMMITS";

    @Override
    public @NotNull VcsLogHighlighter createHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi) {
      return new MergeCommitsHighlighter();
    }

    @Override
    public @NotNull String getId() {
      return ID;
    }

    @Override
    public @NotNull String getTitle() {
      return VcsLogBundle.message("vcs.log.action.highlight.merge.commits");
    }

    @Override
    public boolean showMenuItem() {
      return true;
    }
  }
}
