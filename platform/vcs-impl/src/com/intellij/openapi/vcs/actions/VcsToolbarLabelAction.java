// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VcsToolbarLabelAction extends DumbAwareAction implements CustomComponentAction {

  private static final String DEFAULT_LABEL = "VCS:";

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
    return new VcsToolbarLabel()
      .withFont(JBUI.Fonts.toolbarFont())
      .withBorder(JBUI.Borders.empty(0, 6, 0, 5));
  }

  private static class VcsToolbarLabel extends JBLabel {
    public VcsToolbarLabel() {
      super(DEFAULT_LABEL);
    }

    @Override
    public String getText() {
      Project project = DataManager.getInstance().getDataContext(this).getData(CommonDataKeys.PROJECT);
      return getConsolidatedVcsName(project);
    }
  }

  private static String getConsolidatedVcsName(Project project) {
    String name = DEFAULT_LABEL;
    if (project != null) {
      AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
      List<String> ids = Arrays.stream(vcss).map(vcs -> vcs.getShortName()).distinct().collect(Collectors.toList());
      if (ids.size() == 1) {
        name = ids.get(0) + ":";
      }
    }
    return name;
  }
}
