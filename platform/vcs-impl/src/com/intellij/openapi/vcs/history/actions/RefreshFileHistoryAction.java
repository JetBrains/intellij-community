// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.FileHistoryPanelImpl;

public class RefreshFileHistoryAction extends RefreshAction implements DumbAware {
  public RefreshFileHistoryAction() {
    super(VcsBundle.message("action.name.refresh"), VcsBundle.message("action.description.refresh"), AllIcons.Actions.Refresh);
  }

  public void actionPerformed(AnActionEvent e) {
    FileHistoryPanelImpl panel = (FileHistoryPanelImpl)e.getRequiredData(VcsDataKeys.FILE_HISTORY_PANEL);
    panel.getRefresher().refresh(false);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    FileHistoryPanelImpl panel = (FileHistoryPanelImpl)e.getData(VcsDataKeys.FILE_HISTORY_PANEL);
    if (panel == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(!panel.getRefresher().isInRefresh());
  }
}
