// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.highlighters;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class IndexHighlighter implements VcsLogHighlighter {
  public static final JBColor NOT_INDEXED_COMMIT_FOREGROUND = JBColor.BLUE;
  private final @NotNull VcsLogData myLogData;

  IndexHighlighter(@NotNull VcsLogData logData) {
    myLogData = logData;
  }

  @Override
  public @NotNull VcsCommitStyle getStyle(int commitId, @NotNull VcsShortCommitDetails details, int column, boolean isSelected) {
    if (isSelected || !Registry.is("vcs.log.highlight.not.indexed")) return VcsCommitStyle.DEFAULT;
    VcsLogIndex index = myLogData.getIndex();
    if (!index.isIndexed(commitId)) {
      return VcsCommitStyleFactory.foreground(NOT_INDEXED_COMMIT_FOREGROUND);
    }
    return VcsCommitStyle.DEFAULT;
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
  }

  public static class Factory implements VcsLogHighlighterFactory {
    private static final @NotNull @NonNls String ID = "INDEXED_COMMITS";

    @Override
    public @NotNull VcsLogHighlighter createHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi) {
      return new IndexHighlighter(logData);
    }

    @Override
    public @NotNull String getId() {
      return ID;
    }

    @Override
    public @NotNull String getTitle() {
      return VcsLogBundle.message("vcs.log.action.highlight.indexed.commits");
    }

    @Override
    public boolean showMenuItem() {
      return false;
    }
  }
}
