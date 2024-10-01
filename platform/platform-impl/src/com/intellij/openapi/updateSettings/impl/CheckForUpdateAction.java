// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CheckForUpdateAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    if (ActionPlaces.WELCOME_SCREEN.equals(e.getPlace())) {
      e.getPresentation().setEnabledAndVisible(true);
    }
    else {
      e.getPresentation().setVisible(!ActionPlaces.isMacSystemMenuAction(e));
    }

    if (ExternalUpdateManager.ACTUAL != null) {
      e.getPresentation().setDescription(ActionsBundle.message("action.CheckForUpdate.description.plugins"));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    UpdateChecker.updateAndShowResult(e.getProject());
  }
}
