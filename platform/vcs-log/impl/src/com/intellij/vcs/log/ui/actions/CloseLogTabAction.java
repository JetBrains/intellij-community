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

import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.ContentUtilEx;
import com.intellij.vcs.log.impl.VcsLogContentProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CloseLogTabAction extends CloseTabToolbarAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (e.getProject() == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    ContentManager contentManager = getContentManager(e.getProject());
    if (contentManager == null || getTabbedContent(contentManager) == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(true);
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    ContentManager contentManager = getContentManager(project);
    if (contentManager == null) return;
    TabbedContent tabbedContent = getTabbedContent(contentManager);
    if (tabbedContent == null) return;

    if (tabbedContent.getTabs().size() > 1) {
      JComponent component = tabbedContent.getComponent();
      tabbedContent.removeContent(component);
      contentManager.setSelectedContent(tabbedContent, true, true);
    }
    else {
      contentManager.removeContent(tabbedContent, true);
    }
  }

  @Nullable
  private static TabbedContent getTabbedContent(@NotNull ContentManager contentManager) {
    TabbedContent tabbedContent = ContentUtilEx.findTabbedContent(contentManager, VcsLogContentProvider.TAB_NAME);
    if (tabbedContent != contentManager.getSelectedContent()) return null;
    return tabbedContent;
  }

  @Nullable
  private static ContentManager getContentManager(@NotNull Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
    if (toolWindow == null) return null;
    return toolWindow.getContentManager();
  }
}
