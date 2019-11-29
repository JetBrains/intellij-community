// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

public class ChangesViewToolWindowFactory implements ToolWindowFactory, DumbAware {
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    ChangesViewToolWindowManager.getInstance(project).setToolWindow(toolWindow);
  }

  @Override
  public boolean shouldBeAvailable(@NotNull Project project) {
    return ChangesViewToolWindowManager.getInstance(project).shouldBeAvailable();
  }
}
