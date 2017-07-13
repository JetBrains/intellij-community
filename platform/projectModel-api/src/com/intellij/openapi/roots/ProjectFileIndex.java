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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Provides information about files contained in a project.
 *
 * @see ProjectRootManager#getFileIndex()
 */
public interface ProjectFileIndex extends FileIndex {
  class SERVICE {
    private SERVICE() { }

    public static ProjectFileIndex getInstance(Project project) {
      return ProjectFileIndex.getInstance(project);
    }
  }

  @NotNull
  static ProjectFileIndex getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectFileIndex.class);
  }

  /**
   * Returns module to which content the specified file belongs or null if the file does not belong to content of any module.
   */
  @Nullable
  Module getModuleForFile(@NotNull VirtualFile file);

  /**
   * Returns module to which content the specified file belongs or null if the file does not belong to content of any module.
   *
   * @param honorExclusion if {@code false} the containing module will be returned even if the file is located under a folder marked as excluded
   */
  @Nullable
  Module getModuleForFile(@NotNull VirtualFile file, boolean honorExclusion);

  /**
   * Returns the order entries which contain the specified file (either in CLASSES or SOURCES).
   */
  @NotNull
  List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile file);

  /**
   * Returns a classpath entry to which the specified file or directory belongs.
   *
   * @return the file for the classpath entry, or null if the file is not a compiled
   *         class file or directory belonging to a library.
   */
  @Nullable
  VirtualFile getClassRootForFile(@NotNull VirtualFile file);

  /**
   * Returns the module source root or library source root to which the specified file or directory belongs.
   *
   * @return the file for the source root, or null if the file is not located under any of the source roots for the module.
   */
  @Nullable
  VirtualFile getSourceRootForFile(@NotNull VirtualFile file);

  /**
   * Returns the module content root to which the specified file or directory belongs or null if the file does not belong to content of any module.
   */
  @Nullable
  VirtualFile getContentRootForFile(@NotNull VirtualFile file);

  /**
   * Returns the module content root to which the specified file or directory belongs or null if the file does not belong to content of any module.
   *
   * @param honorExclusion if {@code false} the containing content root will be returned even if the file is located under a folder marked as excluded
   */
  @Nullable
  VirtualFile getContentRootForFile(@NotNull VirtualFile file, final boolean honorExclusion);

  /**
   * Returns the name of the package corresponding to the specified directory.
   *
   * @return the package name, or null if the directory does not correspond to any package.
   */
  @Nullable
  String getPackageNameByDirectory(@NotNull VirtualFile dir); //Q: move to FileIndex?

  /**
   * Returns true if {@code file} is a file which belongs to the classes (not sources) of some library which is included into dependencies
   * of some module.
   */
  boolean isLibraryClassFile(@NotNull VirtualFile file);

  /**
   * Returns true if {@code fileOrDir} is a file or directory from production/test sources of some module or sources of some library which is included into dependencies
   * of some module.
   */
  boolean isInSource(@NotNull VirtualFile fileOrDir);

  /**
   * Returns true if {@code fileOrDir} belongs to classes of some library which is included into dependencies of some module.
   */
  boolean isInLibraryClasses(@NotNull VirtualFile fileOrDir);

  /**
   * @return true if the file belongs to the classes or sources of a library added to dependencies of the project,
   *         false otherwise
   */
  boolean isInLibrary(@NotNull VirtualFile fileOrDir);

  /**
   * Returns true if {@code fileOrDir} is a file or directory from sources of some library which is included into dependencies
   * of some module.
   */
  boolean isInLibrarySource(@NotNull VirtualFile fileOrDir);

  /**
   * @deprecated name of this method may be confusing. If you want to check if the file is excluded or ignored use {@link #isExcluded(VirtualFile)}.
   * If you want to check if the file is ignored use {@link FileTypeRegistry#isFileIgnored(VirtualFile)}.
   * If you want to check if the file or one of its parents is ignored use {@link #isUnderIgnored(VirtualFile)}.
   */
  @Deprecated
  boolean isIgnored(@NotNull VirtualFile file);

  /**
   * Checks if the specified file or directory is located under project roots but the file itself or one of its parent directories is
   * either excluded from the project or ignored by {@link FileTypeRegistry#isFileIgnored(VirtualFile)}).
   *
   * @return true if {@code file} is excluded or ignored, false otherwise.
   */
  boolean isExcluded(@NotNull VirtualFile file);

  /**
   * Checks if the specified file or directory is located under project roots but the file itself or one of its parent directories is ignored
   * by {@link FileTypeRegistry#isFileIgnored(VirtualFile)}).
   *
   * @return true if {@code file} is ignored, false otherwise.
   */
  boolean isUnderIgnored(@NotNull VirtualFile file);
}
