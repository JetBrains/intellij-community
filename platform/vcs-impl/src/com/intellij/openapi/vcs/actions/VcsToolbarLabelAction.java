// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;

public class VcsToolbarLabelAction extends DumbAwareAction implements CustomComponentAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(false);
    Project project = e.getProject();
    e.getPresentation().setVisible(project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    //do nothing
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return new JBLabel("VCS:")
      .withFont(JBUI.Fonts.toolbarFont())
      .withBorder(JBUI.Borders.empty(0, 6, 0, 5));
  }
}
