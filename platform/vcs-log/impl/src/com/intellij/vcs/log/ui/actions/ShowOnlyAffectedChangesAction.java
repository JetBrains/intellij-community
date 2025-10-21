// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.frame.VcsLogAsyncChangesTreeModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ShowOnlyAffectedChangesAction extends BooleanPropertyToggleAction {
  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Boolean hasAffectedFiles = e.getData(VcsLogAsyncChangesTreeModel.HAS_AFFECTED_FILES);
    if (hasAffectedFiles == null || !hasAffectedFiles) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    super.update(e);
  }
}
