// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.focus;

import com.intellij.diagnostic.logs.LogCategory;
import com.intellij.diagnostic.logs.DebugLogLevel;
import com.intellij.diagnostic.logs.LogLevelConfigurationManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class LogFocusRequestsAction extends ToggleAction implements DumbAware {

  private static final String LOGGER_NAME = "jb.focus.requests";

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setVisible(SystemInfo.isJetBrainsJvm);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return Logger.getInstance(LOGGER_NAME).isDebugEnabled();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    LogLevelConfigurationManager logsManager = LogLevelConfigurationManager.getInstance();
    List<LogCategory> logsCategories = logsManager.getState().categories;
    logsCategories.removeIf(c -> LOGGER_NAME.equals(c.getCategory()));
    if (state) {
      logsCategories.add(new LogCategory(LOGGER_NAME, DebugLogLevel.DEBUG));
    }
    logsManager.setCategories(logsCategories);
  }
}
