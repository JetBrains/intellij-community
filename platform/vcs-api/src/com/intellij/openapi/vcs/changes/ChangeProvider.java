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

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsProviderMarker;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * The provider of change information (from the point of view of VCS).
 *
 * @author max
 */
public interface ChangeProvider extends VcsProviderMarker {
  /**
   * <p>Get changes from point of view of VCS. The vcs plugin should invoke
   * methods on the {@code builder} object to report how changes in dirtyScope
   * map to VCS.</p>
   *
   * <p>The implementations of this method should not report changes outside 
   * of the dirty scope, but if these changes are reported, they will be
   * ignored by the caller.</p>
   *
   * @param dirtyScope a changes on the virtual file system
   * @param builder a builder of VCS changes
   * @param progress a current progress object
   * @param addGate
   * @throws VcsException if there there is a VCS specific problem
   */
  void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress,
                  final ChangeListManagerGate addGate) throws VcsException;

  /**
   * Returns true if the initial unsaved modification of a document should cause dirty scope invalidation
   * for the file corresponding to the document.
   *
   * @return true if document modification should mark the scope as dirty, false otherwise
   */
  boolean isModifiedDocumentTrackingRequired();

  /**
   * performs working copy "cleanup"
   * @param files - locked directories
   */
  void doCleanup(final List<VirtualFile> files);
}
