// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.util.ui.ErrorTreeView;
import org.jetbrains.annotations.Nullable;

public final class ContentManagerUtil {
  private ContentManagerUtil() {
  }

  /**
   * This is utility method. It returns {@code ContentManager} from the current context.
   */
  public static @Nullable ContentManager getContentManagerFromContext(DataContext dataContext, boolean requiresVisibleToolWindow) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    String id = toolWindowManager.getActiveToolWindowId();
    if (id == null) {
      if (toolWindowManager.isEditorComponentActive()) {
        id = toolWindowManager.getLastActiveToolWindowId();
      }
    }

    ToolWindowEx toolWindow = id != null ? (ToolWindowEx)toolWindowManager.getToolWindow(id) : null;
    if (requiresVisibleToolWindow && (toolWindow == null || !toolWindow.isVisible())) {
      return null;
    }

    ContentManager fromToolWindow = toolWindow != null ? toolWindow.getContentManager() : null;
    ContentManager fromContext = PlatformDataKeys.CONTENT_MANAGER.getData(dataContext);
    return fromContext == null ? fromToolWindow : fromContext;
  }

  public static void cleanupContents(Content notToRemove, Project project, String contentName) {
    MessageView messageView = MessageView.SERVICE.getInstance(project);
    for (Content content : messageView.getContentManager().getContents()) {
      if (content.isPinned()) continue;
      if (contentName.equals(content.getDisplayName()) && content != notToRemove) {
        ErrorTreeView listErrorView = (ErrorTreeView)content.getComponent();
        if (listErrorView != null) {
          if (messageView.getContentManager().removeContent(content, true)) {
            content.release();
          }
        }
      }
    }
  }
}
