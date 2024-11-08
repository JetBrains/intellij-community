// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.wm.impl.content.BaseLabel;
import com.intellij.ui.content.Content;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ToolWindowContextMenuActionBase extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

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

  public abstract void update(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow, @Nullable Content content);
  public abstract void actionPerformed(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow, @Nullable Content content);

  @ApiStatus.Internal
  public static @Nullable Content getContextContent(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow) {
    BaseLabel baseLabel = ObjectUtils.tryCast(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT), BaseLabel.class);
    Content selectedContent = baseLabel != null ? baseLabel.getContent() : null;
    if (selectedContent == null) {
      selectedContent = toolWindow.getContentManager().getSelectedContent();
    }
    return selectedContent;
  }
}