// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import org.jetbrains.annotations.NotNull;

public class HideMinorChangesAction extends ToggleAction implements DumbAware {

  public HideMinorChangesAction() {
    super("Hide Files with minor changes", "Hide files those have only minor changes like formatting",
          AllIcons.Vcs.Ignore_file);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return VcsApplicationSettings.getInstance().HIDE_MINOR_CHANGES;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    VcsApplicationSettings applicationSettings = VcsApplicationSettings.getInstance();
    applicationSettings.HIDE_MINOR_CHANGES = state;
    applicationSettings.notifyChangeListeners();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(true);
    super.update(e);
  }

}
