// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.*;

import java.nio.file.Path;

public interface IProjectStore extends IComponentStore {
  @TestOnly
  Key<Boolean> COMPONENT_STORE_LOADING_ENABLED = Key.create("COMPONENT_STORE_LOADING_ENABLED");

  @NotNull Path getProjectBasePath();

  @NotNull String getProjectName();

  @NotNull StorageScheme getStorageScheme();
  
  @NotNull String getPresentableUrl();

  /**
   * The path to project configuration file - `misc.xml` for directory-based and `*.ipr` for file-based.
   * @return
   */
  @NotNull Path getProjectFilePath();

  @NotNull Path getWorkspacePath();

  void clearStorages();

  boolean isOptimiseTestLoadSpeed();

  void setOptimiseTestLoadSpeed(boolean optimiseTestLoadSpeed);

  boolean isProjectFile(@NotNull VirtualFile file);

  /**
   * @deprecated Use {@link #getDirectoryStorePath()} or {@link Path#getParent()} of {@link #getProjectFilePath()}.
   */
  @SystemIndependent
  @Deprecated(forRemoval = true)
  @Nullable String getDirectoryStorePath(boolean ignoreProjectStorageScheme);

  /**
   * The directory of project configuration files for directory-based project or null for file-based.
   */
  @Nullable Path getDirectoryStorePath();

  void setPath(@NotNull Path path, boolean isRefreshVfsNeeded, @Nullable Project template);

  @Nullable String getProjectWorkspaceId();
}
