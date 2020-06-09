// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.annotate;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface AnnotationProvider {
  @NotNull
  FileAnnotation annotate(@NotNull VirtualFile file) throws VcsException;

  @NotNull
  FileAnnotation annotate(@NotNull VirtualFile file, VcsFileRevision revision) throws VcsException;

  default String getActionName() {
    return ActionsBundle.message("action.Annotate.text");
  }

  /**
   * Check whether the annotation retrieval is valid (or possible) for the
   * particular file revision (or version in the repository).
   * @param rev File revision to be checked.
   * @return true if annotation it valid for the given revision.
   */
  default boolean isAnnotationValid(@NotNull VcsFileRevision rev) { return true; }
}
