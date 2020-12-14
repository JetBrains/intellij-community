// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListDecorator;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

public final class TaskManagerDecorator implements ChangeListDecorator {
  private final Project myProject;

  public TaskManagerDecorator(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void decorateChangeList(@NotNull LocalChangeList changeList,
                                 @NotNull ColoredTreeCellRenderer cellRenderer,
                                 boolean selected,
                                 boolean expanded,
                                 boolean hasFocus) {
    LocalTask task = TaskManager.getManager(myProject).getAssociatedTask(changeList);
    if (task != null && task.isIssue()) {
      cellRenderer.setIcon(task.getIcon());
    }
  }
}
