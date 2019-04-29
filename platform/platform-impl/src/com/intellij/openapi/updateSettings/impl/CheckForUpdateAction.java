// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

public class CheckForUpdateAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    String place = e.getPlace();
    if (ActionPlaces.WELCOME_SCREEN.equals(place)) {
      e.getPresentation().setEnabledAndVisible(true);
    }
    else {
      e.getPresentation().setVisible(!SystemInfo.isMacSystemMenu || !ActionPlaces.isMainMenuOrShortcut(e.getPlace()));
    }

    if (!UpdateSettings.getInstance().isPlatformUpdateEnabled()) {
      e.getPresentation().setDescription(ActionsBundle.message("action.CheckForUpdate.description.plugins"));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    UpdateChecker.updateAndShowResult(e.getProject(), null);
  }
}