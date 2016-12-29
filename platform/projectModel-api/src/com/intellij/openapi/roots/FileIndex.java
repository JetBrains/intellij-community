/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
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
   * Iterates all files and directories under content roots skipping excluded and ignored files and directories.
   *
   * @param iterator the iterator receiving the files.
   * @return false if files processing was stopped ({@link ContentIterator#processFile(VirtualFile)} returned false)
   */
  boolean iterateContent(@NotNull ContentIterator iterator);

  /**
   * Iterates all files and directories in the content under directory <code>dir</code> (including the directory itself) skipping excluded
   * and ignored files and directories. Does not iterate anything if <code>dir</code> is not in the content.
   *
   * @param dir      the directory the contents of which is iterated.
   * @param iterator the iterator receiving the files.
   * @return false if files processing was stopped ({@link ContentIterator#processFile(VirtualFile)} returned false)
   */
  boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator iterator);

  /**
   * Same as {@link #iterateContentUnderDirectory(VirtualFile, ContentIterator)} but allows to pass additional custom filter to the processor
   *
   * @param dir          the directory the contents of which is iterated.
   * @param iterator     the iterator receiving the files.
   * @param customFilter custom filter for files and directories
   * @return false if files processing was stopped ({@link ContentIterator#processFile(VirtualFile)} returned false)
   */
  boolean iterateContentUnderDirectory(@NotNull VirtualFile dir,
                                       @NotNull ContentIterator iterator,
                                       @NotNull VirtualFileFilter customFilter);

  /**
   * Returns {@code true} if {@code fileOrDir} is a file or directory under a content root of this project or module and not excluded or
   * ignored.
   */
  boolean isInContent(@NotNull VirtualFile fileOrDir);

  /**
   * Returns {@code true} if {@code fileOrDir} is a file located under a sources, tests or resources root and not excluded or ignored.
   * <p/>
   * Note that sometimes a file can belong to the content and be a source file but not belong to sources of the content.
   * This happens if sources of some library are located under the content (so they belong to the project content but not as sources).
   *
   * @param file the file to check.
   * @return true if the file is a source file in the content sources, false otherwise.
   */
  boolean isContentSourceFile(@NotNull VirtualFile file);

  /**
   * Returns {@code true} if {@code fileOrDir} is a file or directory located under a sources, tests or resources root and not excluded or ignored.
   */
  boolean isInSourceContent(@NotNull VirtualFile fileOrDir);

  /**
   * Returns true if {@code fileOrDir} is a file or directory located under a test sources or resources root and not excluded or ignored.
   * <p>
   * Use this method when you really need to check whether the file is under test roots according to project configuration.
   * <p>
   * If you want to determine whether file should be considered as test (e.g. for implementing SearchScope)
   * you'd better use {@link TestSourcesFilter#isTestSources(VirtualFile, Project)} instead
   * which includes {@link ProjectFileIndex#isInTestSourceContent(VirtualFile)} invocation.
   *
   * @param fileOrDir the file or directory to check.
   * @return true if the file or directory belongs to a test source root, false otherwise.
   * @see TestSourcesFilter#isTestSources(VirtualFile, Project)
   */
  boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir);

  /**
   * Returns {@code true} if {@code fileOrDir} is a file or directory located under a source root of type from {@code rootTypes} set and not excluded or ignored
   */
  boolean isUnderSourceRootOfType(@NotNull VirtualFile fileOrDir, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes);
}
