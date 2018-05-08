// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.merge;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsProviderMarker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public interface MergeProvider extends VcsProviderMarker {
  @NotNull
  MergeData loadRevisions(@NotNull VirtualFile file) throws VcsException;

  /**
   * called for text conflict
   */
  void conflictResolvedForFile(@NotNull VirtualFile file);

  boolean isBinary(@NotNull VirtualFile file);

  /**
   * Called after a multiple file merge operation with the specified list of files has been completed.
   */
  default void mergeDone(@NotNull List<VirtualFile> files) {
  }
}
