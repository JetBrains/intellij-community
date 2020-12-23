// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * NB: processing order is different for Added and Deleted files, {@link VcsVFSListener} implementation depends on it.
 * <p/>
 * For DELETED files {@link VFileDeleteEvent} MUST be fired AFTER {@link #ignoreDeleted} method invocation.
 * For ADDED files {@link VFileCreateEvent} CAN be fired BEFORE {@link #ignoreAdded} method invocation, in the same command.
 */
public class VcsFileListenerContextHelper {
  // to ignore by listeners
  private final Set<FilePath> myDeletedContext;
  private final Set<VirtualFile> myAddContext;

  VcsFileListenerContextHelper(final Project project) {
    myDeletedContext = new HashSet<>();
    myAddContext = new HashSet<>();
  }

  public static VcsFileListenerContextHelper getInstance(final Project project) {
    return ServiceManager.getService(project, VcsFileListenerContextHelper.class);
  }

  public void ignoreDeleted(final FilePath filePath) {
    myDeletedContext.add(filePath);
  }

  public boolean isDeletionIgnored(final FilePath filePath) {
    return myDeletedContext.contains(filePath);
  }

  public void ignoreAdded(final VirtualFile virtualFile) {
    myAddContext.add(virtualFile);
  }

  public boolean isAdditionIgnored(final VirtualFile virtualFile) {
    return myAddContext.contains(virtualFile);
  }

  public void clearContext() {
    myAddContext.clear();
    myDeletedContext.clear();
  }

  public boolean isAdditionContextEmpty() {
    return myAddContext.isEmpty();
  }

  public boolean isDeletionContextEmpty() {
    return myDeletedContext.isEmpty();
  }
}
