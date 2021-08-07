// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The provider of the change information (from the point of view of VCS).
 */
public interface ChangeProvider {
  /**
   * <p>Get changes from point of view of VCS. The vcs plugin should invoke
   * methods on the {@code builder} object to report how changes in dirtyScope
   * map to VCS.</p>
   *
   * <p>The implementations of this method should not report changes outside 
   * of the dirty scope, but if these changes are reported, they will be
   * ignored by the caller.</p>
   *
   * @param dirtyScope a set of changes on the virtual file system
   * @param builder a builder of VCS changes
   * @param progress a current progress object
   * @throws VcsException if there is a VCS specific problem
   */
  void getChanges(@NotNull VcsDirtyScope dirtyScope,
                  @NotNull ChangelistBuilder builder,
                  @NotNull ProgressIndicator progress,
                  @NotNull ChangeListManagerGate addGate) throws VcsException;

  /**
   * Returns true if the initial unsaved modification of a document should cause dirty scope invalidation
   * for the file corresponding to the document.
   * <p>
   * Such implementations will need to check {@link FileDocumentManager#getUnsavedDocuments()} or {@link FileDocumentManager#isFileModified(VirtualFile)}
   * to report {@link Change} for files with in-memory-only changes (that are yet unmodified on disk).
   *
   * @return true if document modification should mark the scope as dirty, false otherwise
   */
  boolean isModifiedDocumentTrackingRequired();

  /**
   * Accepts files for which vcs operations are temporarily blocked and tries to "cleanup" them - make vcs operations available again.
   * Such files could be reported using {@link ChangelistBuilder#processLockedFolder(VirtualFile)}.
   * <p>
   * For instance, for Subversion this method is used to perform "svn cleanup" on corresponding locked working copy directories.
   */
  default void doCleanup(@NotNull List<VirtualFile> files) {
  }
}
