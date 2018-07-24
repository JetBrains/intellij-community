// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.merge;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents the state of a multiple file merge operation.
 *
 * @author yole
 * @see MergeProvider2#createMergeSession
 * @since 8.1
 */
public interface MergeSessionEx extends MergeSession {
  /**
   * Called when user executes "Accept Theirs" or "Accept Ours" action to update file content on disk to the selected version.
   *
   * @param resolution AcceptedYours or AcceptedTheirs
   */
  void acceptFilesRevisions(@NotNull List<VirtualFile> files, @NotNull MergeSessionEx.Resolution resolution) throws VcsException;

  /**
   * Called after conflict resolution for conflicting files.
   * @see  #conflictResolvedForFile
   *
   * @param file       the conflicting file.
   * @param resolution the used resolution.
   */
  void conflictResolvedForFiles(@NotNull List<VirtualFile> files, @NotNull Resolution resolution);
}
