// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history;

import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link VcsLogInternalDataKeys#FILE_HISTORY_UI}
 */
public class FileHistoryUiDataRule implements GetDataRule {
  @Override
  public @Nullable Object getData(@NotNull DataProvider dataProvider) {
    VcsLogUi ui = VcsLogDataKeys.VCS_LOG_UI.getData(dataProvider);
    if (ui instanceof FileHistoryUi) {
      return ui;
    }
    return null;
  }
}
