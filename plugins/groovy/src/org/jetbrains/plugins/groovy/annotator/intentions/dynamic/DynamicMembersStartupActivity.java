// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public final class DynamicMembersStartupActivity implements StartupActivity.DumbAware {
  public DynamicMembersStartupActivity() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      throw ExtensionNotApplicableException.create();
    }
  }

  @Override
  public void runActivity(@NotNull Project project) {
    DynamicManager manager = DynamicManager.getInstance(project);
    if (!manager.getRootElement().getContainingClasses().isEmpty()) {
      ToolWindowManager.getInstance(project).invokeLater(() -> {
        // initialize toolWindow
        DynamicToolWindowWrapper.getInstance(project).getToolWindow();
      });
    }
  }
}
