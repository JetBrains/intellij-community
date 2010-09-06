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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;

import java.util.Collection;
import java.util.Set;

/**
 * <p>The dirty scope for a version control system. The instance of this interface
 * is passed to implementers of the {@link com.intellij.openapi.vcs.changes.ChangeProvider}
 * interface to the method {@link ChangeProvider#getChanges(VcsDirtyScope, ChangelistBuilder,com.intellij.openapi.progress.ProgressIndicator, ChangeListManagerGate)}.</p>
 * <p/>
 * <p>The instance of this class is valid only while the project is valid.</p>
 *
 * @author max
 */
public abstract class VcsDirtyScope {
  /**
   * @return a set of content roots affected. The content root
   *         is consdired affected if there is at least one descendant
   *         dirty file or directory which is under this content root.
   * @see #getRecursivelyDirtyDirectories()
   * @see #getDirtyFiles()
   * @see com.intellij.openapi.vcs.ProjectLevelVcsManager#getVcsRootFor(com.intellij.openapi.vcs.FilePath) 
   */
  public abstract Collection<VirtualFile> getAffectedContentRoots();

  /**
   * @return project for this dirty scope
   */
  public abstract Project getProject();

  /**
   * @return the vcs for this dirty scope
   */
  public abstract AbstractVcs getVcs();

  /**
   * Get dirty files and directories. Note if the directory is
   * listed as dirty, all files in it are also considered dirty and
   * they are returned by this method. Note that this method does not list
   * files that are returned by {@link #getRecursivelyDirtyDirectories()}.
   *
   * @return the set of dirty file or directories with all directory children added.
   */
  public abstract Set<FilePath> getDirtyFiles();

  /**
   * Get dirty files and directories. This method differs from
   * {@link #getDirtyFiles()} that it does not adds all children
   * to the set of the dirty files automatically. The invoker should
   * process the children of valid directories themselves.
   *
   * @return the set of dirty file or directories without implied directory children.
   */
  public abstract Set<FilePath> getDirtyFilesNoExpand();

  /**
   * Get recursively dirty directories.
   *
   * @return a directories that are recurisvely dirty.
   */
  public abstract Set<FilePath> getRecursivelyDirtyDirectories();

  public abstract boolean isRecursivelyDirty(final VirtualFile vf);

  /**
   * Invoke the {@code iterator} for all files in the dirty scope.
   * For recursively dirty directories all children are processed.
   *
   * @param iterator an iterator to invoke
   */
  public abstract void iterate(Processor<FilePath> iterator);

  /**
   * Check if the path belongs to the dirty scope.
   *
   * @param path a path to check
   * @return true if path belongs to the dirty scope.
   */
  public abstract boolean belongsTo(final FilePath path);

  public Collection<VirtualFile> getAffectedContentRootsWithCheck() {
    return getAffectedContentRoots();
  }
}
