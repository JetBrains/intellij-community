// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.focus;

import com.intellij.diagnostic.DebugLogManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
    DebugLogManager dlm = DebugLogManager.getInstance();
    List<DebugLogManager.Category> categories = new ArrayList<>(dlm.getSavedCategories());
    dlm.clearCategories(categories);

    categories.removeIf(c -> LOGGER_NAME.equals(c.getCategory()));
    if (state) {
      categories.add(new DebugLogManager.Category(LOGGER_NAME, DebugLogManager.DebugLogLevel.DEBUG));
    }

    dlm.applyCategories(categories);
    dlm.saveCategories(categories);
  }
}
