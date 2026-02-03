// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AnnotationsWriteableFilesVfsListener implements VirtualFileListener {
  private final Project myProject;
  private final VcsKey myVcsKey;

  public AnnotationsWriteableFilesVfsListener(@NotNull Project project, @NotNull VcsKey vcsKey) {
    myProject = project;
    myVcsKey = vcsKey;
  }

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
    if (!event.isFromRefresh()) return;

    if (VirtualFile.PROP_WRITABLE.equals(event.getPropertyName())) {
      if (((Boolean)event.getOldValue()).booleanValue()) {
        closeAnnotations(event.getFile());
      }
    }
  }

  @Override
  public void contentsChanged(@NotNull VirtualFileEvent event) {
    VirtualFile file = event.getFile();
    if (!event.isFromRefresh()) return;
    if (!file.isWritable()) {
      closeAnnotations(file);
    }
  }

  private void closeAnnotations(@NotNull VirtualFile file) {
    VcsAnnotationLocalChangesListener annotationsListener =
      ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener();
    annotationsListener.invalidateAnnotationsFor(file, myVcsKey);
  }
}
