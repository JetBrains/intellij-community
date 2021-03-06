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

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.TabbedContent;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.VcsLogUiEx;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RefreshLogAction extends RefreshAction {
  private static final Logger LOG = Logger.getInstance(RefreshLogAction.class);

  public RefreshLogAction() {
    super(VcsLogBundle.message("action.name.refresh.log"), VcsLogBundle.message("action.description.refresh.log"),
          AllIcons.Actions.Refresh);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    VcsLogManager logManager = e.getRequiredData(VcsLogInternalDataKeys.LOG_MANAGER);

    // diagnostic for possible refresh problems
    VcsLogUi ui = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI);
    if (ui instanceof VcsLogUiEx) {
      VisiblePackRefresher refresher = ((VcsLogUiEx)ui).getRefresher();
      if (!refresher.isValid()) {
        String message = "Trying to refresh invalid log tab '" + ui.getId() + "'.";
        if (!logManager.getDataManager().getProgress().isRunning()) {
          LOG.error(message, collectDiagnosticInformation(e.getProject(), logManager));
        }
        else {
          LOG.warn(message);
        }
        refresher.setValid(true, false);
      }
    }

    logManager.getDataManager().refresh(VcsLogUtil.getVisibleRoots(ui));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VcsLogManager logManager = e.getData(VcsLogInternalDataKeys.LOG_MANAGER);
    e.getPresentation().setEnabledAndVisible(logManager != null && e.getData(VcsLogDataKeys.VCS_LOG_UI) != null);
  }

  private static Attachment @NotNull [] collectDiagnosticInformation(@Nullable Project project, @NotNull VcsLogManager logManager) {
    List<Attachment> result = new ArrayList<>();
    result.add(new Attachment("log-windows.txt", "Log Windows:\n" + logManager.getLogWindowsInformation()));

    if (project != null) {
      ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
      if (toolWindow != null) {
        String contentDump = StringUtil.join(toolWindow.getContentManager().getContents(), content -> {
          if (content instanceof TabbedContent) {
            return content + ", tabs=[" +
                   StringUtil.join(((TabbedContent)content).getTabs(), pair -> pair.first, ", ") + "]";
          }
          return content.toString();
        }, "\n");
        result.add(new Attachment("vcs-tool-window-content.txt",
                                  "Tool Window " + toolWindow.getTitle() + " (" + toolWindow.getType() + "):\n" + contentDump));
      }
    }

    return result.toArray(Attachment.EMPTY_ARRAY);
  }
}
