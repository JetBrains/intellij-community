/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.diff;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsProviderMarker;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface DiffProvider extends VcsProviderMarker {

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
