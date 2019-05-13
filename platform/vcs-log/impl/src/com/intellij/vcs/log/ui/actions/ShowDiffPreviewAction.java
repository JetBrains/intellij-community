// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;

public class ShowDiffPreviewAction extends BooleanPropertyToggleAction {

  public ShowDiffPreviewAction() {
    super("Show Diff Preview", "Show Diff Preview Panel", null);
  }

  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return CommonUiProperties.SHOW_DIFF_PREVIEW;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    FileHistoryUi fileHistoryUi = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI);
    if (fileHistoryUi != null && !fileHistoryUi.hasDiffPreview()) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      super.update(e);
    }
  }
}
