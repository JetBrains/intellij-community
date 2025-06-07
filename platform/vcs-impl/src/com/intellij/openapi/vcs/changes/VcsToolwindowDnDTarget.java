// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.getToolWindowFor;

@ApiStatus.Internal
public abstract class VcsToolwindowDnDTarget extends DnDActivateOnHoldTarget {
  protected final @NotNull Project myProject;
  protected final @NotNull Content myContent;

  protected VcsToolwindowDnDTarget(@NotNull Project project, @NotNull Content content) {
    myProject = project;
    myContent = content;
  }

  @Override
  protected void activateContent() {
    ChangesViewContentManager.getInstance(myProject).setSelectedContent(myContent);
    ToolWindow toolWindow = getToolWindowFor(myProject, myContent.getTabName());
    if (toolWindow != null && !toolWindow.isVisible()) {
      toolWindow.activate(null);
    }
  }
}