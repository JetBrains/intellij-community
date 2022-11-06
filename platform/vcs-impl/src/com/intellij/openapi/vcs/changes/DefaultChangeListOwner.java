// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DefaultChangeListOwner implements ChangeListOwner {
  private final Project myProject;

  public DefaultChangeListOwner(@NotNull Project project) { myProject = project; }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void moveChangesTo(@NotNull LocalChangeList list, Change @NotNull ... changes) {
    ChangeListManager.getInstance(myProject).moveChangesTo(list, changes);
  }

  @Override
  public void addUnversionedFiles(@NotNull LocalChangeList list, @NotNull List<? extends VirtualFile> unversionedFiles) {
    ScheduleForAdditionAction.addUnversionedFilesToVcsInBackground(myProject, list, unversionedFiles);
  }
}
