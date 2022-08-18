// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Allows excluding specific files from being processed by {@link VcsVFSListener}.
 * <p>
 * NB: processing order is different for Added and Deleted files, {@link VcsVFSListener} implementation depends on it.
 * <p/>
 * For DELETED files {@link VFileDeleteEvent} MUST be fired AFTER {@link #ignoreDeleted} method invocation.
 * For ADDED files {@link VFileCreateEvent} CAN be fired BEFORE {@link #ignoreAdded} method invocation, in the same command.
 */
public interface VcsFileListenerContextHelper {

  static VcsFileListenerContextHelper getInstance(@NotNull Project project) {
    return project.getService(VcsFileListenerContextHelper.class);
  }

  void ignoreDeleted(@NotNull Collection<FilePath> filePath);

  boolean isDeletionIgnored(@NotNull FilePath filePath);

  void ignoreAdded(@NotNull Collection<FilePath> filePaths);

  void ignoreAddedRecursive(@NotNull Collection<FilePath> filePaths);

  boolean isAdditionIgnored(@NotNull FilePath filePath);

  void clearContext();

  boolean isAdditionContextEmpty();

  boolean isDeletionContextEmpty();
}
