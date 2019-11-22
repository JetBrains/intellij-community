// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;

public class StartUseVcsAction extends DumbAwareAction {
  public StartUseVcsAction() {
    super(VcsBundle.message("action.enable.version.control.integration.text"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VcsDataWrapper data = new VcsDataWrapper(e);
    boolean enabled = data.enabled();

    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(enabled);
    if (enabled) {
      presentation.setText(VcsBundle.message("action.enable.version.control.integration.text"));
    }
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    VcsDataWrapper data = new VcsDataWrapper(e);
    boolean enabled = data.enabled();
    if (!enabled) {
      return;
    }

    StartUseVcsDialog dialog = new StartUseVcsDialog(data);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      String vcsName = dialog.getVcs();
      if (vcsName.length() > 0) {
        ProjectLevelVcsManager manager = data.getManager();
        AbstractVcs vcs = manager.findVcsByName(vcsName);
        assert vcs != null : "No vcs found for name " + vcsName;
        vcs.enableIntegration();
      }
    }
  }
}
