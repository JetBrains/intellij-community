// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.indexing.IndexingBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class ToggleDumbModeAction extends DumbAwareAction {
  private static final Key<Boolean> DUMB = Key.create("ToggleDumbModeAction");

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    if (isToggledDumb(project)) {
      setToggledDumb(project, false);
    }
    else {
      setToggledDumb(project, true);
      new MyDumbModeTask(project).queue(project);
    }
  }

  private static void setToggledDumb(Project project, boolean value) {
    project.putUserData(DUMB, value);
  }

  private static boolean isToggledDumb(Project project) {
    return project.getUserData(DUMB) == Boolean.TRUE;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    boolean dumb = DumbServiceImpl.getInstance(project).isDumb();
    if (!dumb && isToggledDumb(project)) {
      setToggledDumb(project, false);
    }
    e.getPresentation().setEnabled(!dumb || isToggledDumb(project));
    e.getPresentation().setText(dumb ? "Exit Dumb Mode" : "Enter Dumb Mode");
  }

  private static class MyDumbModeTask extends DumbModeTask {
    private final Project myProject;

    private MyDumbModeTask(Project project) {
      myProject = project;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      indicator.setText(IndexingBundle.message("toggled.dumb.mode"));
      while (isToggledDumb(myProject)) {
        indicator.checkCanceled();
        TimeoutUtil.sleep(100);
      }
    }

    @Override
    public @Nullable DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
      if (taskFromQueue instanceof MyDumbModeTask && ((MyDumbModeTask)taskFromQueue).myProject.equals(myProject)) return this;
      return null;
    }
  }
}
