// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class VcsActionGroup extends DefaultActionGroup implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);

    Presentation presentation = event.getPresentation();
    Project project = event.getProject();
    if (project == null || !project.isOpen()) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
    }
    else {
      presentation.setVisible(true);
      presentation.setEnabled(true);
    }
  }
}
