// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.highlighters;

import com.intellij.vcs.log.VcsLogHighlighter;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.data.VcsLogData;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface VcsLogHighlighterFactory {
  @NotNull
  VcsLogHighlighter createHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi);

  @NotNull
  String getId();

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  String getTitle();

  boolean showMenuItem();
}
