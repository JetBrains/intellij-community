/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class VcsLogToolbarPopupActionGroup extends DefaultActionGroup {

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @Override
  public boolean canBePerformed(DataContext context) {
    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ListPopup popup = JBPopupFactory.getInstance()
                                    .createActionGroupPopup(null, this, e.getDataContext(), JBPopupFactory.ActionSelectionAid.MNEMONICS,
                                                            true,
                                                            VcsLogActionPlaces.VCS_LOG_TOOLBAR_POPUP_PLACE);
    Component component = e.getInputEvent().getComponent();
    if (component instanceof ActionButtonComponent) {
      popup.showUnderneathOf(component);
    }
    else {
      popup.showInCenterOf(component);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLogUi logUi = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    e.getPresentation().setEnabledAndVisible(project != null && logUi != null);
  }
}
