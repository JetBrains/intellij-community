/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.UIBundle;

import javax.swing.*;

/**
 * This action is not visible in the UI but we keep it available to let users invoke it from keyboard.
 *
 * @author pti
 *         Date: Mar 2, 2005
 */
public class RecentProjectsAction extends WelcomePopupAction {
  protected void fillActions(final DefaultActionGroup group) {
    final AnAction[] recentProjectActions = RecentProjectsManagerBase.getInstance().getRecentProjectsActions(false);
    for (AnAction action : recentProjectActions) {
      group.add(action);
    }
  }

  protected String getTextForEmpty() {
    return UIBundle.message("welcome.screen.recent.projects.action.no.recent.projects.to.display.action.name");
  }

  protected String getCaption() {
    return "";
  }

  @Override
  protected boolean isSilentlyChooseSingleOption() {
    return false;
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible(RecentProjectsManagerBase.getInstance().getRecentProjectsActions(false).length > 0);
  }

  @Override
  protected void showPopup(DataContext context, ListPopup popup, JComponent contextComponent) {
    popup.showInBestPositionFor(context);
  }
}
