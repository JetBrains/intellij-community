// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import org.jetbrains.annotations.NotNull;

public class VcsLogToolbarPopupActionGroup extends DefaultActionGroup implements DumbAware {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, this, e.getDataContext(),
                                                                          JBPopupFactory.ActionSelectionAid.MNEMONICS, true,
                                                                          ActionPlaces.VCS_LOG_TOOLBAR_POPUP_PLACE);
    popup.setShowSubmenuOnHover(true);
    PopupUtil.showForActionButtonEvent(popup, e);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLogUi logUi = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    e.getPresentation().setEnabledAndVisible(project != null && logUi != null);
    e.getPresentation().setPerformGroup(true);
  }
}
