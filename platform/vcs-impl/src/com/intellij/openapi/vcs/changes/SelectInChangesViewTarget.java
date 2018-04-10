/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
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
    return VcsBundle.message("changes.toolwindow.name");
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

  public String getToolWindowId() {
    return ChangesViewContentManager.TOOLWINDOW_ID;
  }

  public float getWeight() {
    return 9;
  }
}
