// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.util.paths.RecursiveFilePathSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
public class VcsFileListenerContextHelperImpl implements VcsFileListenerContextHelper {
  private final Object LOCK = new Object();
  private final Set<FilePath> myIgnoredDeleted = new HashSet<>();
  private final Set<FilePath> myIgnoredAdded = new HashSet<>();
  private final RecursiveFilePathSet myIgnoredAddedRecursive = new RecursiveFilePathSet(SystemInfo.isFileSystemCaseSensitive);

  @Override
  public void ignoreDeleted(@NotNull Collection<? extends FilePath> filePath) {
    synchronized (LOCK) {
      myIgnoredDeleted.addAll(filePath);
    }
  }

  @Override
  public boolean isDeletionIgnored(@NotNull FilePath filePath) {
    synchronized (LOCK) {
      return myIgnoredDeleted.contains(filePath);
    }
  }

  @Override
  public void ignoreAdded(@NotNull Collection<? extends FilePath> filePaths) {
    synchronized (LOCK) {
      myIgnoredAdded.addAll(filePaths);
    }
  }

  @Override
  public void ignoreAddedRecursive(@NotNull Collection<? extends FilePath> filePaths) {
    synchronized (LOCK) {
      myIgnoredAddedRecursive.addAll(filePaths);
    }
  }

  @Override
  public boolean isAdditionIgnored(@NotNull FilePath filePath) {
    synchronized (LOCK) {
      return myIgnoredAdded.contains(filePath) ||
             myIgnoredAddedRecursive.hasAncestor(filePath);
    }
  }

  @Override
  public void clearContext() {
    synchronized (LOCK) {
      myIgnoredAdded.clear();
      myIgnoredAddedRecursive.clear();
      myIgnoredDeleted.clear();
    }
  }

  @Override
  public boolean isAdditionContextEmpty() {
    synchronized (LOCK) {
      return myIgnoredAdded.isEmpty() && myIgnoredAddedRecursive.isEmpty();
    }
  }

  @Override
  public boolean isDeletionContextEmpty() {
    synchronized (LOCK) {
      return myIgnoredDeleted.isEmpty();
    }
  }
}
