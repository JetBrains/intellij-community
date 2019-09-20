// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogProperties;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.table.VcsLogColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class PreferCommitDateAction extends BooleanPropertyToggleAction implements DumbAware {
  public PreferCommitDateAction() {
    super(VcsBundle.message("prefer.commit.timestamp.action.text"),
          VcsBundle.message("prefer.commit.timestamp.action.description"), null);
  }

  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return CommonUiProperties.PREFER_COMMIT_DATE;
  }

  @Override
  protected boolean isVisible(@NotNull AnActionEvent e) {
    if (!isDateDisplayed(e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES))) {
      return false;
    }
    Set<VcsLogProvider> providers = e.getData(VcsLogDataKeys.VCS_LOG_PROVIDERS);
    return providers != null && providers.stream().anyMatch(VcsLogProperties.HAS_COMMITTER::getOrDefault);
  }

  private static boolean isDateDisplayed(@Nullable VcsLogUiProperties properties) {
    if (properties != null && properties.exists(CommonUiProperties.COLUMN_ORDER)) {
      List<Integer> columnOrder = properties.get(CommonUiProperties.COLUMN_ORDER);
      return columnOrder.contains(VcsLogColumn.DATE.ordinal());
    }
    return false;
  }
}
