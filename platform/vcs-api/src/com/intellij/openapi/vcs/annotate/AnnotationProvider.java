// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see com.intellij.vcs.AnnotationProviderEx
 */
public interface AnnotationProvider {
  @NotNull
  FileAnnotation annotate(@NotNull VirtualFile file) throws VcsException;

  @NotNull
  FileAnnotation annotate(@NotNull VirtualFile file, VcsFileRevision revision) throws VcsException;

  default @Nullable AnnotationWarning getAnnotationWarnings(@NotNull FileAnnotation fileAnnotation) {
    return null;
  }

  /**
   * Override this method to provide a different name for the 'Annotate' action.
   */
  default @Nls(capitalization = Nls.Capitalization.Title) @Nullable String getCustomActionName() {
    return null;
  }

  /**
   * @deprecated override {@link #getCustomActionName()} instead.
   */
  @Deprecated
  default @Nls(capitalization = Nls.Capitalization.Title) String getActionName() {
    return "";
  }

  /**
   * Check whether the annotation retrieval is valid (or possible) for the
   * particular file revision (or version in the repository).
   *
   * @param rev File revision to be checked.
   * @return true if annotation it valid for the given revision.
   */
  default boolean isAnnotationValid(@NotNull VcsFileRevision rev) { return true; }
}
