// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions.history;

import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.data.DataGetter;
import com.intellij.vcs.log.data.VcsLogData;
import org.jetbrains.annotations.NotNull;

public abstract class FileHistoryMetadataAction extends FileHistoryOneCommitAction<VcsCommitMetadata> {
  @Override
  protected @NotNull DataGetter<VcsCommitMetadata> getDetailsGetter(@NotNull VcsLogData logData) {
    return logData.getMiniDetailsGetter();
  }
}
