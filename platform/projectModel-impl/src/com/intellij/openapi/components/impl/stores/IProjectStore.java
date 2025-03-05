// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Path;

public interface IProjectStore extends IComponentStore {
  @TestOnly
  Key<Boolean> COMPONENT_STORE_LOADING_ENABLED = Key.create("COMPONENT_STORE_LOADING_ENABLED");

  @NotNull Path getProjectBasePath();

  @ApiStatus.Internal
  @NotNull String getLocationHash();

  @NotNull String getProjectName();

  @NotNull StorageScheme getStorageScheme();
  
  @NotNull String getPresentableUrl();

  /**
   * The path to project configuration file - `misc.xml` for directory-based and `*.ipr` for file-based.
   */
  @NotNull Path getProjectFilePath();

  @NotNull Path getWorkspacePath();

  @ApiStatus.Internal
  void clearStorages();

  @ApiStatus.Internal
  boolean isOptimiseTestLoadSpeed();

  @ApiStatus.Internal
  void setOptimiseTestLoadSpeed(boolean optimiseTestLoadSpeed);

  boolean isProjectFile(@NotNull VirtualFile file);

  /**
   * The directory of project configuration files for a directory-based project or null for file-based.
   */
  @Nullable Path getDirectoryStorePath();

  @ApiStatus.Internal
  void setPath(@NotNull Path path, @Nullable Project template);

  @Nullable String getProjectWorkspaceId();
}
