// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.diff;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface DiffProvider {

  @Nullable
  VcsRevisionNumber getCurrentRevision(VirtualFile file);

  @Nullable
  ItemLatestState getLastRevision(VirtualFile virtualFile);

  @Nullable
  ItemLatestState getLastRevision(final FilePath filePath);

  @Nullable
  ContentRevision createFileContent(VcsRevisionNumber revisionNumber, VirtualFile selectedFile);

  @Nullable
  VcsRevisionNumber getLatestCommittedRevision(VirtualFile vcsRoot);

  @Nullable
  default ContentRevision createCurrentFileContent(VirtualFile file) {
    VcsRevisionNumber revisionNumber = getCurrentRevision(file);
    if (revisionNumber == null) return null;
    return createFileContent(revisionNumber, file);
  }

  /**
   * Preload base revisions of all the given changes, if the DiffProvider supports it.
   */
  default void preloadBaseRevisions(@NotNull VirtualFile root, @NotNull Collection<Change> changes) {
  }

  default boolean canCompareWithWorkingDir() {
    return false;
  }

  @Nullable
  default Collection<Change> compareWithWorkingDir(@NotNull VirtualFile fileOrDir,
                                                   @NotNull VcsRevisionNumber revNum) throws VcsException {
    return null;
  }
}
