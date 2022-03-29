// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class VcsFileListenerContextHelperImpl implements VcsFileListenerContextHelper {
  // to ignore by listeners
  private final Set<FilePath> myDeletedContext;
  private final Set<FilePath> myAddContext;

  VcsFileListenerContextHelperImpl() {
    myDeletedContext = new HashSet<>();
    myAddContext = new HashSet<>();
  }

  @Override
  public void ignoreDeleted(@NotNull FilePath filePath) {
    myDeletedContext.add(filePath);
  }

  @Override
  public boolean isDeletionIgnored(@NotNull FilePath filePath) {
    return myDeletedContext.contains(filePath);
  }

  @Override
  public void ignoreAdded(@NotNull FilePath filePath) {
    myAddContext.add(filePath);
  }

  @Override
  public boolean isAdditionIgnored(@NotNull FilePath filePath) {
    return myAddContext.contains(filePath);
  }

  @Override
  public void clearContext() {
    myAddContext.clear();
    myDeletedContext.clear();
  }

  @Override
  public boolean isAdditionContextEmpty() {
    return myAddContext.isEmpty();
  }

  @Override
  public boolean isDeletionContextEmpty() {
    return myDeletedContext.isEmpty();
  }
}
