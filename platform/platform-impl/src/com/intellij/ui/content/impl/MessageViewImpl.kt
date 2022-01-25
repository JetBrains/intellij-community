// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.*;
import com.intellij.ui.UIBundle;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.MessageView;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

final class MessageViewImpl implements MessageView {
  private ToolWindow myToolWindow;
  private final List<Runnable> myPostponedRunnables = new ArrayList<>();

  MessageViewImpl(@NotNull Project project) {
    StartupManager.getInstance(project).runAfterOpened(() -> {
      AppUIExecutor.onUiThread().expireWith(project).submit(() -> {
        myToolWindow = ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask.closable(
          ToolWindowId.MESSAGES_WINDOW, UIBundle.messagePointer("tool.window.name.messages"),
          AllIcons.Toolwindows.ToolWindowMessages, ToolWindowAnchor.BOTTOM));
        ContentManagerWatcher.watchContentManager(myToolWindow, getContentManager());
        for (Runnable postponedRunnable : myPostponedRunnables) {
          postponedRunnable.run();
        }
        myPostponedRunnables.clear();
      });
    });
  }

  @Override
  public ContentManager getContentManager() {
    return myToolWindow.getContentManager();
  }

  @Override
  public void runWhenInitialized(@NotNull Runnable runnable) {
    if (myToolWindow == null) {
      myPostponedRunnables.add(runnable);
    }
    else {
      runnable.run();
    }
  }
}
