// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class RecursiveFilePathSet {
  private final FilePathMapping<FilePath> myMapping;

  public RecursiveFilePathSet(boolean caseSensitive) {
    myMapping = new FilePathMapping<>(caseSensitive);
  }

  public void add(@NotNull FilePath filePath) {
    myMapping.add(filePath.getPath(), filePath);
  }

  public void addAll(@NotNull Collection<? extends FilePath> filePath) {
    for (FilePath path : filePath) {
      add(path);
    }
  }

  public void remove(@NotNull FilePath filePath) {
    myMapping.remove(filePath.getPath());
  }

  public boolean isEmpty() {
    return myMapping.values().isEmpty();
  }

  public void clear() {
    myMapping.clear();
  }

  public boolean contains(@NotNull FilePath filePath) {
    return myMapping.containsKey(filePath.getPath());
  }

  public boolean hasAncestor(@NotNull FilePath filePath) {
    return myMapping.getMappingFor(filePath.getPath()) != null;
  }

  @NotNull
  public Collection<FilePath> filePaths() {
    return myMapping.values();
  }
}
