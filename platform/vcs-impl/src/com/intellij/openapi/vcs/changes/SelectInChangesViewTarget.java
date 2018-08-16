// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;

/**
 * @author yole
 */
public class SelectInChangesViewTarget implements SelectInTarget, DumbAware {
  private final Project myProject;

  public SelectInChangesViewTarget(final Project project) {
    myProject = project;
  }

  public String toString() {
    return ChangesViewContentManager.LOCAL_CHANGES;
  }

  @Override
  public boolean canSelect(final SelectInContext context) {
    final VirtualFile file = context.getVirtualFile();
    FileStatus fileStatus = ChangeListManager.getInstance(myProject).getStatus(file);
    return ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss().length != 0 &&
           !fileStatus.equals(FileStatus.NOT_CHANGED);
  }

  @Override
  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    final VirtualFile file = context.getVirtualFile();
    Runnable runnable = () -> {
      ChangesViewContentManager.getInstance(myProject).selectContent(ChangesViewContentManager.LOCAL_CHANGES);
      ChangesViewManager.getInstance(myProject).selectFile(file);
    };
    if (requestFocus) {
      ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID).activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  @Override
  public String getToolWindowId() {
    return ChangesViewContentManager.TOOLWINDOW_ID;
  }

  @Override
  public float getWeight() {
    return 9;
  }
}
