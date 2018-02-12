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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;

public class FocusTextFilterAction extends DumbAwareAction {
  public FocusTextFilterAction() {
    super("Focus Text Filter", "Focus text filter or move focus back to the commits list", null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    e.getPresentation().setEnabledAndVisible(project != null && ui instanceof VcsLogUiImpl);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUtil.triggerUsage(e);

    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    MainFrame mainFrame = ((VcsLogUiImpl)e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI)).getMainFrame();
    if (IdeFocusManager.getInstance(project).getFocusedDescendantFor(mainFrame.getToolbar()) != null) {
      IdeFocusManager.getInstance(project).requestFocus(mainFrame.getGraphTable(), true);
    }
    else {
      IdeFocusManager.getInstance(project).requestFocus(mainFrame.getTextFilter(), true);
    }
  }
}
