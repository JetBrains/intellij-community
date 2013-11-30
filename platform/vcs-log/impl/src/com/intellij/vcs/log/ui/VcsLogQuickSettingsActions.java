/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.vcs.log.VcsLogSettings;
import com.intellij.vcs.log.impl.VcsLogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public class VcsLogQuickSettingsActions extends DumbAwareAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    VcsLogSettings settings = ServiceManager.getService(project, VcsLogSettings.class);
    VcsLogManager logManager = ServiceManager.getService(project, VcsLogManager.class);
    VcsLogUI logUi = logManager.getLogUi();
    if (logUi == null) {
      return;
    }

    ActionGroup settingsGroup = new MySettingsActionGroup(settings, logUi);
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, settingsGroup);
    int x = 0;
    int y = 0;
    InputEvent inputEvent = e.getInputEvent();
    if (inputEvent instanceof MouseEvent) {
      x = ((MouseEvent)inputEvent).getX();
      y = ((MouseEvent)inputEvent).getY();
    }
    popupMenu.getComponent().show(inputEvent.getComponent(), x, y);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      VcsLogManager logManager = ServiceManager.getService(project, VcsLogManager.class);
      e.getPresentation().setEnabledAndVisible(logManager.getLogUi() != null);
    }
  }

  private static class MySettingsActionGroup extends ActionGroup {

    private final VcsLogSettings mySettings;
    private final VcsLogUI myUi;

    public MySettingsActionGroup(VcsLogSettings settings, VcsLogUI ui) {
      mySettings = settings;
      myUi = ui;
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[] {
        new ToggleAction("Show Branches Panel") {
          @Override
          public boolean isSelected(AnActionEvent e) {
            return mySettings.isShowBranchesPanel();
          }

          @Override
          public void setSelected(AnActionEvent e, boolean state) {
            mySettings.setShowBranchesPanel(state);
            myUi.setBranchesPanelVisible(state);
          }
        }
      };
    }
  }
}
