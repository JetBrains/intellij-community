/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Set;

/**
 * Provides information about files contained in a project or module.
 *
 * @see ProjectRootManager#getFileIndex()
 * @see ModuleRootManager#getFileIndex()
 */
public interface FileIndex {
  /**
   * Iterates all files and directories in the content.
   *
   * @param iterator the iterator receiving the files.
   * @return false if files processing was stopped ({@link ContentIterator#processFile(VirtualFile)} returned false)
   */
  boolean iterateContent(@NotNull ContentIterator iterator);

  /**
   * Iterates all files and directories in the content under directory <code>dir</code> (including the directory itself).
   * Does not iterate anything if <code>dir</code> is not in the content.
   *
   * @param dir      the directory the contents of which is iterated.
   * @param iterator the iterator receiving the files.
   * @return false if files processing was stopped ({@link ContentIterator#processFile(VirtualFile)} returned false)
   */
  boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator iterator);

  /**
   * Returns true if <code>fileOrDir</code> is a file or directory under a content root of this
   * project or module.
   *
   * @param fileOrDir the file or directory to check.
   * @return true if the file or directory belongs to a content root, false otherwise.
   */
  boolean isInContent(@NotNull VirtualFile fileOrDir);

  /**
   * Returns true if <code>file</code> is a source file which belongs to sources of the content.
   * (Returns true for both source and test source).<p/>
   * Note that sometimes a file can belong to the content and be a source file but not belong to sources of the content.
   * This happens if sources of some library are located under the content (so they belong to the project content but not as sources).
   *
   * @param file the file to check.
   * @return true if the file is a source file in the content sources, false otherwise.
   */
  boolean isContentSourceFile(@NotNull VirtualFile file);

  /**
   * Returns true if <code>fileOrDir</code> is a file or directory from the content source.
   * (Returns true for both source and test source).
   *
   * @param fileOrDir the file or directory to check.
   * @return true if the file or directory belongs to a source or test source root, false otherwise.
   */
  boolean isInSourceContent(@NotNull VirtualFile fileOrDir);

  /**
   * Returns true if <code>fileOrDir</code> is a file or directory from the test content source
   *
   * @param fileOrDir the file or directory to check.
   * @return true if the file or directory belongs to a test source root, false otherwise.
   */
  boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir);

  /**
   * Returns true if <code>fileOrDir</code> is a file or directory from the source root which have
   *
   * @param fileOrDir the file or directory to check.
   * @return true if the file or directory belongs to a source root of one of specified types, false otherwise
   */
  boolean isUnderSourceRootOfType(@NotNull VirtualFile fileOrDir, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes);
}
