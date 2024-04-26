// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Query;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * This is internal class providing implementation for {@link com.intellij.openapi.roots.ProjectFileIndex}. 
 * It will be removed when all code switches to use the new implementation (IDEA-276394). 
 * All plugins which still use this class must be updated to use {@link com.intellij.openapi.roots.ProjectFileIndex} and other APIs instead.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
public abstract class DirectoryIndex {
  public static DirectoryIndex getInstance(Project project) {
    // todo enable later when all usages will be fixed
    //assert !project.isDefault() : "Must not call DirectoryIndex for default project";
    return project.getService(DirectoryIndex.class);
  }

  @NotNull
  public abstract
  Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources);

  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, @NotNull GlobalSearchScope scope) {
    return getDirectoriesByPackageName(packageName, true).filtering(scope::contains);
  }

  @Nullable
  public abstract String getPackageName(@NotNull VirtualFile dir);

  @NotNull
  public abstract List<OrderEntry> getOrderEntries(@NotNull VirtualFile fileOrDir);

  /**
   * @return names of unloaded modules which directly or transitively via exported dependencies depend on the specified module
   */
  @NotNull
  public abstract Set<String> getDependentUnloadedModules(@NotNull Module module);
}
