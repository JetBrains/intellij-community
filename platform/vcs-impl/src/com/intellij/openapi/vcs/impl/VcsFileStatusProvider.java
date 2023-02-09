// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link  FileStatusManager} instead.
 */
@Service
@Deprecated(forRemoval = true)
public final class VcsFileStatusProvider {
  private final Project myProject;

  public static VcsFileStatusProvider getInstance(@NotNull Project project) {
    return project.getService(VcsFileStatusProvider.class);
  }

  VcsFileStatusProvider(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public FileStatus getFileStatus(@NotNull final VirtualFile virtualFile) {
    return ChangeListManager.getInstance(myProject).getStatus(virtualFile);
  }

  public void refreshFileStatusFromDocument(@NotNull final VirtualFile virtualFile, @NotNull final Document doc) {
    FileStatusManager.getInstance(myProject).fileStatusChanged(virtualFile);
  }
}