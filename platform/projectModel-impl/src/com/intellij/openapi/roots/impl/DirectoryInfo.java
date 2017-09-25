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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DirectoryInfo {
  /**
   * @return {@code true} if the whole directory is located under project content or library roots and not excluded or ignored
   * @deprecated use {@link #isInProject(VirtualFile)} instead, this method doesn't take {@link ContentEntry#getExcludePatterns()} into account
   */
  public abstract boolean isInProject();

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
   * @return {@code true} if the whole directory is located in project content, output or library root but excluded from the project
   * @deprecated use {@link #isExcluded(VirtualFile)} instead, this method doesn't take {@link ContentEntry#getExcludePatterns()} into account
   */
  public abstract boolean isExcluded();

  /**
   * Returns {@code true} if {@code file} located under this directory is excluded from the project. If {@code file} is a directory it means
   * that all of its content is recursively excluded from the project.
   *
   * @param file a file under the directory described by this instance.
   */
  public abstract boolean isExcluded(@NotNull VirtualFile file);

  /**
   * Returns {@code true} if {@code file} is located under a module source root and not excluded or ignored
   */
  public abstract boolean isInModuleSource(@NotNull VirtualFile file);

  /**
   * @deprecated use {@link #isInModuleSource(VirtualFile)} instead, this method doesn't take {@link ContentEntry#getExcludePatterns() exclude patterns} into account
   */
  public abstract boolean isInModuleSource();

  /**
   * @return {@code true} if {@code file} located under this directory is located in library sources.
   * @deprecated use {@link #isInLibrarySource(VirtualFile)} instead, this method doesn't take {@link SyntheticLibrary#getExcludeFileCondition()} into account
   */
  public abstract boolean isInLibrarySource();

  /**
   * @param file a file under the directory described by this instance.
   * @return {@code true} if {@code file} located under this directory is located in library sources.
   * If {@code file} is a directory it means that all of its content is recursively in not part of the libraries.
   */
  public abstract boolean isInLibrarySource(@NotNull VirtualFile file);

  @Nullable
  public abstract VirtualFile getSourceRoot();

  public abstract int getSourceRootTypeId();

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
}
