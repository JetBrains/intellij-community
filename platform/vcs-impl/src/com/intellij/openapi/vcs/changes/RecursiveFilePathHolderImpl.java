// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@ApiStatus.Internal
public class RecursiveFilePathHolderImpl implements FilePathHolder {

  private final Project myProject;
  private final Set<FilePath> myMap;

  public RecursiveFilePathHolderImpl(final Project project) {
    myProject = project;
    myMap = new HashSet<>();
  }

  @Override
  public void cleanAll() {
    myMap.clear();
  }

  @Override
  public void addFile(@NotNull FilePath file) {
    if (!containsFile(file)) {
      myMap.add(file);
    }
  }

  @Override
  public RecursiveFilePathHolderImpl copy() {
    final RecursiveFilePathHolderImpl copyHolder = new RecursiveFilePathHolderImpl(myProject);
    copyHolder.myMap.addAll(myMap);
    return copyHolder;
  }

  @Override
  public boolean containsFile(@NotNull FilePath file, @NotNull VirtualFile vcsRoot) {
    return containsFile(file);
  }

  private boolean containsFile(@NotNull FilePath file) {
    if (myMap.isEmpty()) return false;
    FilePath parent = file;
    while (parent != null) {
      if (myMap.contains(parent)) return true;
      parent = parent.getParentPath();
    }
    return false;
  }

  @NotNull
  @Override
  public Collection<FilePath> values() {
    return myMap;
  }

  @Override
  public void cleanUnderScope(@NotNull VcsDirtyScope scope) {
    if (myProject.isDisposed()) return;
    final Iterator<FilePath> iterator = myMap.iterator();
    while (iterator.hasNext()) {
      final FilePath file = iterator.next();
      if (isFileDirty(scope, file)) {
        iterator.remove();
      }
    }
  }

  private static boolean isFileDirty(@NotNull VcsDirtyScope scope, @NotNull FilePath filePath) {
    return scope.belongsTo(filePath);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final RecursiveFilePathHolderImpl that = (RecursiveFilePathHolderImpl)o;
    return myMap.equals(that.myMap);
  }

  public int hashCode() {
    return myMap.hashCode();
  }
}
