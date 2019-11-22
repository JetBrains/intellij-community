// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.MessageView;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class MessageViewImpl implements MessageView {
  private ToolWindow myToolWindow;
  private final List<Runnable> myPostponedRunnables = new ArrayList<>();

  public MessageViewImpl(@NotNull Project project) {
    final Runnable runnable = () -> {
      myToolWindow = ToolWindowManager.getInstance(project).registerToolWindow(ToolWindowId.MESSAGES_WINDOW, true, ToolWindowAnchor.BOTTOM, project, true);
      myToolWindow.setIcon(AllIcons.Toolwindows.ToolWindowMessages);
      new ContentManagerWatcher(myToolWindow, getContentManager());
      for (Runnable postponedRunnable : myPostponedRunnables) {
        postponedRunnable.run();
      }
      myPostponedRunnables.clear();
    };
    if (project.isInitialized()) {
      runnable.run();
    }
    else {
      StartupManager.getInstance(project).registerPostStartupActivity(runnable);
    }
  }

  @Override
  public ContentManager getContentManager() {
    return myToolWindow.getContentManager();
  }

  @Override
  public void runWhenInitialized(final Runnable runnable) {
    if (myToolWindow != null) {
      runnable.run();
    }
    else {
      myPostponedRunnables.add(runnable);
    }
  }
}
