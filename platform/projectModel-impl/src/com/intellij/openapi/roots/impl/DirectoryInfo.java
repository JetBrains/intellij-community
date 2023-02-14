// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is internal class used from the old implementation of {@link com.intellij.openapi.roots.ProjectFileIndex}.
 * It will be removed when all code switches to use the new implementation (IDEA-276394).
 * All plugins which still use this class must be updated to use {@link com.intellij.openapi.roots.ProjectFileIndex} and other APIs instead.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
public abstract class DirectoryInfo {

  /**
   * @param file a file under the directory described by this instance.
   * @return {@code true} if {@code file} is located under project content or library roots and not excluded or ignored
   */
  public abstract boolean isInProject(@NotNull VirtualFile file);

  /**
   * @return {@code true} if located under ignored directory
   */
  public abstract boolean isIgnored();

  /**
   * Returns {@code true} if {@code file} located under this directory is excluded from the project. If {@code file} is a directory it means
   * that all of its content is recursively excluded from the project (except the sub-directories which are explicitly included back, e.g. module roots)
   *
   * @param file a file under the directory described by this instance.
   */
  public abstract boolean isExcluded(@NotNull VirtualFile file);

  /**
   * Returns {@code true} if {@code file} is located under a module source root and not excluded or ignored
   */
  public abstract boolean isInModuleSource(@NotNull VirtualFile file);

  /**
   * @param file a file under the directory described by this instance.
   * @return {@code true} if {@code file} located under this directory is located in library sources.
   * If {@code file} is a directory it means that all of its content is recursively in not part of the libraries.
   */
  public abstract boolean isInLibrarySource(@NotNull VirtualFile file);

  @Nullable
  public abstract VirtualFile getSourceRoot();
  @Nullable
  public abstract SourceFolder getSourceRootFolder();

  public boolean hasLibraryClassRoot() {
    return getLibraryClassRoot() != null;
  }

  public abstract VirtualFile getLibraryClassRoot();

  @Nullable
  public abstract VirtualFile getContentRoot();

  @Nullable
  public abstract Module getModule();

  /**
   * Return name of an unloaded module to which content this file or directory belongs
   * or {@code null} if it doesn't belong to an unloaded module.
   * @see com.intellij.openapi.module.UnloadedModuleDescription
   */
  @Nullable
  public abstract String getUnloadedModuleName();

  /**
   * if {@code dir} is excluded and there are content entries under the {@code dir}, process them all (as long as {@code processor} returns true) and
   * @return true if all called processors returned true, and false otherwise
   */
  public abstract boolean processContentBeneathExcluded(@NotNull VirtualFile dir, @NotNull Processor<? super VirtualFile> processor);
}
