/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Enables showing diff between revisions and comparing file or directory state between a revision and a local version in log-based file history.
 * Methods of this class could be called from EDT, so it should deal with it appropriately by starting a background task for long operations,
 * eg for loading revisions content.
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
}
