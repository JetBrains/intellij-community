// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeletedFilesHolder implements FileHolder {
  private final Map<String, LocallyDeletedChange> myFiles = new HashMap<>();

  @Override
  public void cleanAll() {
    myFiles.clear();
  }

  public void takeFrom(final DeletedFilesHolder holder) {
    myFiles.clear();
    myFiles.putAll(holder.myFiles);
  }

  @Override
  public void cleanAndAdjustScope(@NotNull final VcsModifiableDirtyScope scope) {
    final List<LocallyDeletedChange> currentFiles = new ArrayList<>(myFiles.values());
    for (LocallyDeletedChange change : currentFiles) {
      if (scope.belongsTo(change.getPath())) {
        myFiles.remove(change.getPresentableUrl());
      }
    }
  }

  public void addFile(final LocallyDeletedChange change) {
    myFiles.put(change.getPresentableUrl(), change);
  }

  public List<LocallyDeletedChange> getFiles() {
    return new ArrayList<>(myFiles.values());
  }

  public boolean isContainedInLocallyDeleted(final FilePath filePath) {
    final String url = filePath.getPresentableUrl();
    return myFiles.containsKey(url);
  }

  @Override
  public DeletedFilesHolder copy() {
    final DeletedFilesHolder copyHolder = new DeletedFilesHolder();
    copyHolder.myFiles.putAll(myFiles);
    return copyHolder;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DeletedFilesHolder that = (DeletedFilesHolder)o;

    if (!myFiles.equals(that.myFiles)) return false;

    return true;
  }

  public int hashCode() {
    return myFiles.hashCode();
  }
}
