/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

public interface IProjectStore extends IComponentStore {
  @SystemIndependent
  @NotNull
  String getProjectBasePath();

  @NotNull
  String getProjectName();

  @NotNull
  StorageScheme getStorageScheme();

  @SystemIndependent
  @NotNull
  String getProjectFilePath();

  /**
   * `null` for default or non-directory based project.
   */
  @SystemIndependent
  @Nullable
  String getProjectConfigDir();

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
  @SystemIndependent
  String getDirectoryStorePath(boolean ignoreProjectStorageScheme);

  /**
   * Directory of project configuration files for directory-based project. Or null.
   */
  @SystemIndependent
  default String getDirectoryStorePath() {
    return getDirectoryStorePath(false);
  }

  @NotNull
  @SystemIndependent
  String getDirectoryStorePathOrBase();
}
