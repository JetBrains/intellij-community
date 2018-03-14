// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.DefaultCustomComponentAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

public class VcsToolbarLabelAction extends DefaultCustomComponentAction {
  public VcsToolbarLabelAction() {
    super(createVcsLabel());
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(false);

    Project project = e.getProject();
    e.getPresentation().setVisible(project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss());
  }

  private static JComponent createVcsLabel() {
    JBLabel label = new JBLabel("VCS:");
    label.setFont(UIUtil.getToolbarFont());
    label.setBorder(JBUI.Borders.empty(0, 6, 0, 5));
    return label;
  }
}
