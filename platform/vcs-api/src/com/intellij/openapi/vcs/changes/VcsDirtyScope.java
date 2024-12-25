// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * <p>The dirty scope for a version control system. The instance of this interface
 * is passed to implementers of the {@link ChangeProvider}
 * interface to the method {@link ChangeProvider#getChanges(VcsDirtyScope, ChangelistBuilder, com.intellij.openapi.progress.ProgressIndicator, ChangeListManagerGate)}.</p>
 * <p/>
 * <p>The instance of this class is valid only while the project is valid.</p>
 *
 * @author max
 */
public abstract class VcsDirtyScope {
  /**
   * @return a set of content roots affected. The content root
   * is considered affected if there is at least one descendant
   * dirty file or directory which is under this content root.
   * @see #getRecursivelyDirtyDirectories()
   * @see #getDirtyFiles()
   * @see com.intellij.openapi.vcs.ProjectLevelVcsManager#getVcsRootFor(FilePath)
   */
  public abstract Collection<VirtualFile> getAffectedContentRoots();

  /**
   * @return project for this dirty scope
   */
  public abstract @NotNull Project getProject();

  /**
   * @return the vcs for this dirty scope
   */
  public abstract @NotNull AbstractVcs getVcs();

  /**
   * Get dirty files and directories.
   * Note, if a directory is listed as dirty, all files in it are also considered dirty, and they are returned by this method.
   * Note that this method does not list files that are returned by {@link #getRecursivelyDirtyDirectories()}.
   *
   * @return the set of dirty files or directories with all directory children added.
   */
  public abstract Set<FilePath> getDirtyFiles();

  /**
   * Get dirty files and directories. This method differs from
   * {@link #getDirtyFiles()} that it does not add all children
   * to the set of the dirty files automatically. The invoker should
   * process the children of valid directories themselves.
   *
   * @return the set of dirty files or directories without implied directory children.
   */
  public abstract Set<FilePath> getDirtyFilesNoExpand();

  /**
   * Get recursively dirty directories.
   *
   * @return a directories that are recursively dirty.
   */
  public abstract Set<FilePath> getRecursivelyDirtyDirectories();

  public abstract boolean isEmpty();

  /**
   * Check if the path belongs to the dirty scope.
   *
   * @param path a path to check
   */
  public abstract boolean belongsTo(final FilePath path);

  public abstract boolean wasEveryThingDirty();
}
