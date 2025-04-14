// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.util.paths.RecursiveFilePathSet;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Allows excluding specific files from being processed by {@link VcsVFSListener}.
 * <p>
 * NB: processing order is different for Added and Deleted files, {@link VcsVFSListener} implementation depends on it.
 * <p/>
 * For DELETED files {@link VFileDeleteEvent} MUST be fired AFTER {@link #ignoreDeleted} method invocation.
 * For ADDED files {@link VFileCreateEvent} CAN be fired BEFORE {@link #ignoreAdded} method invocation, in the same command.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
public final class VcsFileListenerContextHelper {
  private final Object LOCK = new Object();
  private final Set<FilePath> myIgnoredDeleted = new HashSet<>();
  private final Set<FilePath> myIgnoredAdded = new HashSet<>();
  private final RecursiveFilePathSet myIgnoredAddedRecursive = new RecursiveFilePathSet(SystemInfo.isFileSystemCaseSensitive);

  public void ignoreDeleted(@NotNull Collection<? extends FilePath> filePath) {
    synchronized (LOCK) {
      myIgnoredDeleted.addAll(filePath);
    }
  }

  private boolean isDeletionIgnored(@NotNull FilePath filePath) {
    synchronized (LOCK) {
      return myIgnoredDeleted.contains(filePath);
    }
  }

  public void ignoreAdded(@NotNull Collection<? extends FilePath> filePaths) {
    synchronized (LOCK) {
      myIgnoredAdded.addAll(filePaths);
    }
  }

  public void ignoreAddedRecursive(@NotNull Collection<? extends FilePath> filePaths) {
    synchronized (LOCK) {
      myIgnoredAddedRecursive.addAll(filePaths);
    }
  }

  private boolean isAdditionIgnored(@NotNull FilePath filePath) {
    synchronized (LOCK) {
      return myIgnoredAdded.contains(filePath) ||
             myIgnoredAddedRecursive.hasAncestor(filePath);
    }
  }

  public void clearContext() {
    synchronized (LOCK) {
      myIgnoredAdded.clear();
      myIgnoredAddedRecursive.clear();
      myIgnoredDeleted.clear();
    }
  }

  public static VcsFileListenerContextHelper getInstance(@NotNull Project project) {
    return project.getService(VcsFileListenerContextHelper.class);
  }

  @ApiStatus.Internal
  public static class IgnoredFilesProvider implements VcsFileListenerIgnoredFilesProvider {
    @Override
    public boolean isDeletionIgnored(@NotNull Project project, @NotNull FilePath filePath) {
      return getInstance(project).isDeletionIgnored(filePath);
    }

    @Override
    public boolean isAdditionIgnored(@NotNull Project project, @NotNull FilePath filePath) {
      return getInstance(project).isAdditionIgnored(filePath);
    }
  }
}
