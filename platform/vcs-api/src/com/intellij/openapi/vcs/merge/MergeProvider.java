// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.merge;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsProviderMarker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;


public interface MergeProvider extends VcsProviderMarker {
  @NotNull
  MergeData loadRevisions(@NotNull VirtualFile file) throws VcsException;

  /**
   * called for text conflict
   */
  void conflictResolvedForFile(@NotNull VirtualFile file);

  boolean isBinary(@NotNull VirtualFile file);

  /**
   * Creates a merge dialog customizer used when the default "Resolve conflicts" operation is invoked.
   */
  default MergeDialogCustomizer createDefaultMergeDialogCustomizer() {
    return new MergeDialogCustomizer();
  }
}
