// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Enables showing diff between revisions and comparing file or directory state between a revision and a local version in log-based file history.
 * Methods of this class could be called from EDT, so it should deal with it appropriately by starting a background task for long operations,
 * e.g. for loading revisions content.
 */
public interface VcsLogDiffHandler {

  /**
   * Show diff between (the after-state of) two revisions in the specified files or directories.
   *
   * @param root      repository root.
   * @param leftPath  path to the file on the left, null means file is missing in this revision.
   * @param leftHash  hash of the revision on the left.
   * @param rightPath path to the file on the right, null means file is missing in this revision.
   * @param rightHash hash of the revision on the right.
   */
  void showDiff(@NotNull VirtualFile root,
                @Nullable FilePath leftPath, @NotNull Hash leftHash,
                @Nullable FilePath rightPath, @NotNull Hash rightHash);

  /**
   * Show diff between (the after-state of) specified revision and local version for the specified file or directory.
   *
   * @param root         repository root.
   * @param revisionPath path to the file in the specified revision, null means file is not present in the revision.
   * @param hash         hash of the revision.
   * @param localPath    local path to the file.
   */
  void showDiffWithLocal(@NotNull VirtualFile root,
                         @Nullable FilePath revisionPath,
                         @NotNull Hash hash, @NotNull FilePath localPath);

  /**
   * Show diff between two revisions or between a revision and a local version for the specified set of paths.
   *
   * @param root          repository root.
   * @param affectedPaths paths for which to show diff, null means to show all changes between revisions.
   * @param leftRevision  hash of the revision on the left.
   * @param rightRevision hash of the revision on the right or null if local revision is required.
   */
  void showDiffForPaths(@NotNull VirtualFile root, @Nullable Collection<FilePath> affectedPaths,
                        @NotNull Hash leftRevision, @Nullable Hash rightRevision);

  /**
   * Create a ContentRevision for specified path and commit.
   *
   * @param filePath file path to create content revision for.
   * @param hash     hash of the revision.
   * @return content revision.
   */
  @NotNull
  ContentRevision createContentRevision(@NotNull FilePath filePath, @NotNull Hash hash);
}
