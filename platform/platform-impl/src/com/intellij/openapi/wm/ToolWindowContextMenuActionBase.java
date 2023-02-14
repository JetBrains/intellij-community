// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.wm.impl.content.BaseLabel;
import com.intellij.ui.content.Content;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ToolWindowContextMenuActionBase extends AnAction {
  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    ToolWindow toolWindow = e.getDataContext().getData(PlatformDataKeys.TOOL_WINDOW);
    if (toolWindow == null) {
      return;
    }
    Content content = getContextContent(e, toolWindow);
    actionPerformed(e, toolWindow, content);
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    ToolWindow toolWindow = e.getDataContext().getData(PlatformDataKeys.TOOL_WINDOW);
    if (toolWindow == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    Content content = getContextContent(e, toolWindow);
    update(e, toolWindow, content);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  public abstract void update(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow, @Nullable Content content);
  public abstract void actionPerformed(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow, @Nullable Content content);

  @Nullable
  private static Content getContextContent(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow) {
    BaseLabel baseLabel = ObjectUtils.tryCast(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT), BaseLabel.class);
    Content selectedContent = baseLabel != null ? baseLabel.getContent() : null;
    if (selectedContent == null) {
      selectedContent = toolWindow.getContentManager().getSelectedContent();
    }
    return selectedContent;
  }
}