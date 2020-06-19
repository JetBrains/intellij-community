// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.nio.file.Path;
import java.nio.file.Paths;

public interface IProjectStore extends IComponentStore {
  @NotNull
  Path getProjectBasePath();

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

  void clearStorages();

  boolean isOptimiseTestLoadSpeed();

  void setOptimiseTestLoadSpeed(boolean optimiseTestLoadSpeed);

  boolean isProjectFile(@NotNull VirtualFile file);

  @Nullable
  @SystemIndependent
  String getDirectoryStorePath(boolean ignoreProjectStorageScheme);

  /**
   * Directory of project configuration files for directory-based project. Or null.
   */
  default Path getDirectoryStorePath() {
    String result = getDirectoryStorePath(false);
    return result == null ? null : Paths.get(result);
  }

  @NotNull
  @SystemIndependent
  String getDirectoryStorePathOrBase();

  void setPath(@NotNull Path path, boolean isRefreshVfsNeeded, @Nullable Project template);

  @Nullable
  String getProjectWorkspaceId();
}
