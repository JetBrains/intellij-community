// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

public class DirtBuilder implements DirtBuilderReader {
  private final FileTypeManager myFileTypeManager = FileTypeManager.getInstance();

  private final MultiMap<AbstractVcs, FilePath> myFiles = MultiMap.createSet();
  private final MultiMap<AbstractVcs, FilePath> myDirs = MultiMap.createSet();
  private boolean myEverythingDirty = false;

  public DirtBuilder() {
  }

  public DirtBuilder(@NotNull DirtBuilder builder) {
    myDirs.putAllValues(builder.myDirs);
    myFiles.putAllValues(builder.myFiles);
    myEverythingDirty = builder.myEverythingDirty;
  }

  public void reset() {
    myFiles.clear();
    myDirs.clear();
    myEverythingDirty = false;
  }

  public void everythingDirty() {
    myEverythingDirty = true;
  }

  public void addDirtyFile(@NotNull AbstractVcs vcs, @NotNull FilePath file) {
    if (myFileTypeManager.isFileIgnored(file.getName())) return;
    myFiles.putValue(vcs, file);
  }

  public void addDirtyDirRecursively(@NotNull AbstractVcs vcs, @NotNull FilePath dir) {
    if (myFileTypeManager.isFileIgnored(dir.getName())) return;
    myDirs.putValue(vcs, dir);
  }

  @Override
  public boolean isEverythingDirty() {
    return myEverythingDirty;
  }

  @Override
  @NotNull
  public MultiMap<AbstractVcs, FilePath> getFilesForVcs() {
    return myFiles;
  }

  @Override
  @NotNull
  public MultiMap<AbstractVcs, FilePath> getDirsForVcs() {
    return myDirs;
  }

  @Override
  public boolean isEmpty() {
    return myFiles.isEmpty() && myDirs.isEmpty();
  }
}
