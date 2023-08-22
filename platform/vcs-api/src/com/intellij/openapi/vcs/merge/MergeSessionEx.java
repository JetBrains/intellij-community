// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.merge;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents the state of a multiple file merge operation.
 *
 * @see MergeProvider2#createMergeSession
 */
public interface MergeSessionEx extends MergeSession {
  /**
   * Called when user executes "Accept Theirs" or "Accept Ours" action to update file content on disk to the selected version.
   *
   * @param files      the conflicting files.
   * @param resolution AcceptedYours or AcceptedTheirs
   */
  void acceptFilesRevisions(@NotNull List<? extends VirtualFile> files, @NotNull Resolution resolution) throws VcsException;

  /**
   * Called after conflict resolution for conflicting files.
   *
   * @param files      the conflicting files.
   * @param resolution the used resolution.
   * @see #conflictResolvedForFile
   */
  void conflictResolvedForFiles(@NotNull List<? extends VirtualFile> files, @NotNull Resolution resolution);
}
