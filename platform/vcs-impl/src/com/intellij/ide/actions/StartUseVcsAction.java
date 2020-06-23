// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StartUseVcsAction extends DumbAwareAction {
  public StartUseVcsAction() {
    super(VcsBundle.messagePointer("action.enable.version.control.integration.text"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);

    boolean enabled = isEnabled(project);

    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(enabled);
    if (enabled) {
      presentation.setText(VcsBundle.messagePointer("action.enable.version.control.integration.text"));
    }
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    if (!isEnabled(project)) return;

    StartUseVcsDialog dialog = new StartUseVcsDialog(project);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      String vcsName = dialog.getVcs();
      if (vcsName.length() > 0) {
        AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).findVcsByName(vcsName);
        assert vcs != null : "No vcs found for name " + vcsName;
        vcs.enableIntegration();
      }
    }
  }

  private static boolean isEnabled(@Nullable Project project) {
    if (project == null) return false;
    ProjectLevelVcsManagerImpl manager = ProjectLevelVcsManagerImpl.getInstanceImpl(project);
    return manager.haveVcses() && !manager.hasAnyMappings();
  }
}
