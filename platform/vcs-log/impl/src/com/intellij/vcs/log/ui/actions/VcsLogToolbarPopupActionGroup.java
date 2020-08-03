// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.AutoPopupSupportingListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.popup.util.PopupState;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.InputEvent;

public class VcsLogToolbarPopupActionGroup extends DefaultActionGroup {
  private final PopupState myPopupState = new PopupState();

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @Override
  public boolean canBePerformed(@NotNull DataContext context) {
    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (myPopupState.isRecentlyHidden()) return; // do not show new popup
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, this, e.getDataContext(),
                                                                          JBPopupFactory.ActionSelectionAid.MNEMONICS, true,
                                                                          VcsLogActionPlaces.VCS_LOG_TOOLBAR_POPUP_PLACE);
    popup.addListener(myPopupState);
    AutoPopupSupportingListener.installOn(popup);
    InputEvent inputEvent = e.getInputEvent();
    if (inputEvent == null) {
      popup.showInFocusCenter();
    }
    else {
      Component component = inputEvent.getComponent();
      if (component instanceof ActionButtonComponent) {
        popup.showUnderneathOf(component);
      }
      else {
        popup.showInCenterOf(component);
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLogUi logUi = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    e.getPresentation().setEnabledAndVisible(project != null && logUi != null);
  }
}
