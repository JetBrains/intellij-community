// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions.history;

import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.data.DataGetter;
import com.intellij.vcs.log.history.FileHistoryUi;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class FileHistoryMetadataAction extends FileHistorySingleCommitAction<VcsCommitMetadata> {
  @NotNull
  @Override
  protected List<VcsCommitMetadata> getSelection(@NotNull FileHistoryUi ui) {
    return ui.getVcsLog().getSelectedShortDetails();
  }

  @NotNull
  @Override
  protected DataGetter<VcsCommitMetadata> getDetailsGetter(@NotNull FileHistoryUi ui) {
    return ui.getLogData().getMiniDetailsGetter();
  }
}
