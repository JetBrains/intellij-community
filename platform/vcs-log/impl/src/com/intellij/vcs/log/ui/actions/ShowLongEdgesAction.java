// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ShowLongEdgesAction extends BooleanPropertyToggleAction {
  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return MainVcsLogUiProperties.SHOW_LONG_EDGES;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    if (ui != null && ui.getDataPack().isEmpty()) e.getPresentation().setEnabled(false);
  }
}
