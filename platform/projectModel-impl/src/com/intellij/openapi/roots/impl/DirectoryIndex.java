// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.List;
import java.util.Set;

/**
 * This class is internal low-level API. Consider using {@link com.intellij.openapi.roots.ProjectFileIndex} instead of using this class directly.
 */
public abstract class DirectoryIndex {
  public static DirectoryIndex getInstance(Project project) {
    assert !project.isDefault() : "Must not call DirectoryIndex for default project";
    return ServiceManager.getService(project, DirectoryIndex.class);
  }

  @NotNull
  public abstract DirectoryInfo getInfoForFile(@NotNull VirtualFile file);

  @Nullable
  public abstract SourceFolder getSourceRootFolder(@NotNull DirectoryInfo info);

  @Nullable
  public abstract JpsModuleSourceRootType<?> getSourceRootType(@NotNull DirectoryInfo info);

  @NotNull
  public abstract
  Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources);

  @Nullable
  public abstract String getPackageName(@NotNull VirtualFile dir);

  @NotNull
  public abstract List<OrderEntry> getOrderEntries(@NotNull DirectoryInfo info);

  /**
   * @return names of unloaded modules which directly or transitively via exported dependencies depend on the specified module
   */
  @NotNull
  public abstract Set<String> getDependentUnloadedModules(@NotNull Module module);
}
