// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.util.containers.JBIterable;
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
    ToolWindow toolWindow = JBIterable.of(PlatformDataKeys.LAST_ACTIVE_TOOL_WINDOWS.getData(dataContext)).first();
    if (requiresVisibleToolWindow && (toolWindow == null || !toolWindow.isVisible())) {
      return null;
    }

    ContentManager fromToolWindow = toolWindow != null ? toolWindow.getContentManagerIfCreated() : null;
    ContentManager fromContext = PlatformDataKeys.CONTENT_MANAGER.getData(dataContext);
    return fromContext == null ? fromToolWindow : fromContext;
  }

  public static void cleanupContents(Content notToRemove, Project project, String contentName) {
    MessageView messageView = MessageView.getInstance(project);
    for (Content content : messageView.getContentManager().getContents()) {
      if (content.isPinned()) continue;
      if (contentName.equals(content.getDisplayName()) && content != notToRemove) {
        if (messageView.getContentManager().removeContent(content, true)) {
          content.release();
        }
      }
    }
  }
}
