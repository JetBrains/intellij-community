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

import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class MergeCommitsHighlighter implements VcsLogHighlighter {

  @NotNull private final VcsLogUi myLogUi;

  public MergeCommitsHighlighter(@NotNull VcsLogUi logUi) {
    myLogUi = logUi;
  }

  @NotNull
  @Override
  public VcsCommitStyle getStyle(@NotNull VcsShortCommitDetails details, boolean isSelected) {
    if (isSelected || !myLogUi.isHighlighterEnabled(Factory.ID)) return VcsCommitStyle.DEFAULT;
    if (details.getParents().size() >= 2) {
      Color color = EditorColorsUtil.getGlobalOrDefaultColor(VcsLogColors.MERGED_COMMIT);
      return VcsCommitStyleFactory.foreground(color);
    }
    return VcsCommitStyle.DEFAULT;
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
  }

  public static class Factory implements VcsLogHighlighterFactory {
    @NotNull private static final String ID = "MERGE_COMMITS";

    @NotNull
    @Override
    public VcsLogHighlighter createHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi) {
      return new MergeCommitsHighlighter(logUi);
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

    @Override
    public boolean showMenuItem() {
      return true;
    }
  }
}
