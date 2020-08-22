// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.vcs.history.VcsAbstractHistorySession;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author irengrig
 */
public interface VcsCacheableAnnotationProvider {
  VcsAnnotation createCacheable(final FileAnnotation fileAnnotation);
  @Nullable
  FileAnnotation restore(@NotNull VcsAnnotation vcsAnnotation,
                         @NotNull VcsAbstractHistorySession session,
                         @NotNull @NonNls String annotatedContent,
                         boolean forCurrentRevision,
                         VcsRevisionNumber revisionNumber);
}
