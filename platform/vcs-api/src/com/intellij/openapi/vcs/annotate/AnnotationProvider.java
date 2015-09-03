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
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsProviderMarker;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface AnnotationProvider extends VcsProviderMarker {
  @NotNull
  FileAnnotation annotate(@NotNull VirtualFile file) throws VcsException;

  @NotNull
  FileAnnotation annotate(@NotNull VirtualFile file, VcsFileRevision revision) throws VcsException;

  /**
   * Check whether the annotation retrieval is valid (or possible) for the
   * particular file revision (or version in the repository).
   * @param rev File revision to be checked.
   * @return true if annotation it valid for the given revision.
   */
  boolean isAnnotationValid(@NotNull VcsFileRevision rev);
}
