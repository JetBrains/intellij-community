// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogProperties;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PreferCommitDateAction extends ToggleAction implements DumbAware {
  public PreferCommitDateAction() {
    super("Show Commit Timestamp", "Show the time when the change was committed, rather than authored.", null);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    VcsLogApplicationSettings settings = ApplicationManager.getApplication().getService(VcsLogApplicationSettings.class);
    return settings != null && Boolean.TRUE.equals(settings.get(CommonUiProperties.PREFER_COMMIT_DATE));
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    VcsLogApplicationSettings settings = ApplicationManager.getApplication().getService(VcsLogApplicationSettings.class);
    if (settings != null) {
      settings.set(CommonUiProperties.PREFER_COMMIT_DATE, state);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isVisible(e));
    super.update(e);
  }

  protected boolean isVisible(@NotNull AnActionEvent e) {
    if (!Boolean.TRUE.equals(e.getData(VcsLogDataKeys.VCS_DATE_DISPLAYED))) {
      return false;
    }
    Set<VcsLogProvider> providers = e.getData(VcsLogDataKeys.VCS_LOG_PROVIDERS);
    return providers != null && providers.stream().anyMatch(VcsLogProperties.HAS_COMMITTER::getOrDefault);
  }
}
