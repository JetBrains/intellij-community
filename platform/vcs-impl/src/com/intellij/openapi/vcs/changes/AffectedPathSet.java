// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.PathUtilRt;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

class AffectedPathSet {
  private final Set<CharSequence> myDirectParents = CollectionFactory.createCharSequenceSet(SystemInfoRt.isFileSystemCaseSensitive);
  private final Set<CharSequence> myAllPaths = CollectionFactory.createCharSequenceSet(SystemInfoRt.isFileSystemCaseSensitive);

  AffectedPathSet(@NotNull Collection<FilePath> paths) {
    for (FilePath path : paths) {
      add(path);
    }
  }

  private void add(@NotNull FilePath filePath) {
    // do not store 'subString' to reduce memory footprint
    CharSequence parent = PathUtilRt.getParentPathSequence(new CharSequenceSubSequence(filePath.getPath()));
    if (parent.isEmpty()) return;

    myDirectParents.add(parent);

    while (!parent.isEmpty()) {
      boolean wasAdded = myAllPaths.add(parent);
      if (!wasAdded) break;
      parent = PathUtilRt.getParentPathSequence(parent);
    }
  }

  public @NotNull ThreeState haveChangesUnder(@NotNull FilePath filePath) {
    String path = filePath.getPath();
    if (myDirectParents.contains(path)) return ThreeState.YES;
    if (myAllPaths.contains(path)) return ThreeState.UNSURE;
    return ThreeState.NO;
  }
}
