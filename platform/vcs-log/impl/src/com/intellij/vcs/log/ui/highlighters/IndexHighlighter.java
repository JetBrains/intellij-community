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

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import org.jetbrains.annotations.NotNull;

public class IndexHighlighter implements VcsLogHighlighter {
  public static final JBColor NOT_INDEXED_COMMIT_FOREGROUND = JBColor.BLUE;
  @NotNull private final VcsLogData myLogData;

  public IndexHighlighter(@NotNull VcsLogData logData) {
    myLogData = logData;
  }

  @NotNull
  @Override
  public VcsCommitStyle getStyle(@NotNull VcsShortCommitDetails details, boolean isSelected) {
    if (isSelected || !Registry.is("vcs.log.highlight.not.indexed")) return VcsCommitStyle.DEFAULT;
    VcsLogIndex index = myLogData.getIndex();
    if (!index.isIndexed(myLogData.getCommitIndex(details.getId(), details.getRoot()))) {
      return VcsCommitStyleFactory.foreground(NOT_INDEXED_COMMIT_FOREGROUND);
    }
    return VcsCommitStyle.DEFAULT;
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
  }

  public static class Factory implements VcsLogHighlighterFactory {
    @NotNull private static final String ID = "INDEXED_COMMITS";

    @NotNull
    @Override
    public VcsLogHighlighter createHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi) {
      return new IndexHighlighter(logData);
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @NotNull
    @Override
    public String getTitle() {
      return "Indexed Commits";
    }

    @Override
    public boolean showMenuItem() {
      return false;
    }
  }
}
