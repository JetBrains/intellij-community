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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IProjectStore extends IComponentStore {
  /**
   * System-independent path.
   */
  @Nullable
  String getProjectBasePath();

  @NotNull
  String getProjectName();

  @NotNull
  StorageScheme getStorageScheme();

  /**
   * System-independent path.
   */
  @NotNull
  String getProjectFilePath();

  /**
   * System-independent path.
   */
  @Nullable
  String getWorkspaceFilePath();

  void loadProjectFromTemplate(@NotNull Project project);

  void clearStorages();

  boolean isOptimiseTestLoadSpeed();

  void setOptimiseTestLoadSpeed(boolean optimiseTestLoadSpeed);

  boolean isProjectFile(@NotNull VirtualFile file);

  /**
   * Directory of project configuration files for directory-based project. Or null.
   */
  @Nullable
  VirtualFile getDirectoryStoreFile();

  @Nullable
  String getDirectoryStorePath(boolean ignoreProjectStorageScheme);

  /**
   * Directory of project configuration files for directory-based project. Or null.
   */
  default String getDirectoryStorePath() {
    return getDirectoryStorePath(false);
  }

  @NotNull
  String getDirectoryStorePathOrBase();
}
