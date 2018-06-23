/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
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
