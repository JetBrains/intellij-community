// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChange;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public class ShelvedChangeListDragBean {
  private final @NotNull List<ShelvedChange> myShelvedChanges;
  private final @NotNull List<ShelvedBinaryFile> myBinaries;
  private final @NotNull List<ShelvedChangeList> myShelvedChangelists;

  public ShelvedChangeListDragBean(@NotNull List<ShelvedChange> shelvedChanges,
                                   @NotNull List<ShelvedBinaryFile> binaries,
                                   @NotNull List<ShelvedChangeList> shelvedChangelists) {
    myShelvedChanges = shelvedChanges;
    myBinaries = binaries;
    myShelvedChangelists = shelvedChangelists;
  }

  public @NotNull List<ShelvedChange> getChanges() {
    return myShelvedChanges;
  }

  public @NotNull List<ShelvedBinaryFile> getBinaryFiles() {
    return myBinaries;
  }

  public @NotNull List<ShelvedChangeList> getShelvedChangelists() {
    return myShelvedChangelists;
  }
}
