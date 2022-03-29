// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class VcsFileListenerContextHelperImpl implements VcsFileListenerContextHelper {
  // to ignore by listeners
  private final Set<FilePath> myIgnoredDeleted;
  private final Set<FilePath> myIgnoredAdded;

  VcsFileListenerContextHelperImpl() {
    myIgnoredDeleted = new HashSet<>();
    myIgnoredAdded = new HashSet<>();
  }

  @Override
  public void ignoreDeleted(@NotNull Collection<FilePath> filePath) {
    myIgnoredDeleted.addAll(filePath);
  }

  @Override
  public boolean isDeletionIgnored(@NotNull FilePath filePath) {
    return myIgnoredDeleted.contains(filePath);
  }

  @Override
  public void ignoreAdded(@NotNull Collection<FilePath> filePath) {
    myIgnoredAdded.addAll(filePath);
  }

  @Override
  public boolean isAdditionIgnored(@NotNull FilePath filePath) {
    return myIgnoredAdded.contains(filePath);
  }

  @Override
  public void clearContext() {
    myIgnoredAdded.clear();
    myIgnoredDeleted.clear();
  }

  @Override
  public boolean isAdditionContextEmpty() {
    return myIgnoredAdded.isEmpty();
  }

  @Override
  public boolean isDeletionContextEmpty() {
    return myIgnoredDeleted.isEmpty();
  }
}
