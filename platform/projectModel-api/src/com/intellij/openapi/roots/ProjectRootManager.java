// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Allows to query and modify the list of root files and directories belonging to a project.
 */
@ApiStatus.NonExtendable
public abstract class ProjectRootManager extends SimpleModificationTracker {
  /**
   * Returns the project root manager instance for the specified project.
   *
   * @param project the project for which the instance is requested.
   * @return the instance.
   */
  public static ProjectRootManager getInstance(@NotNull Project project) {
    return project.getService(ProjectRootManager.class);
  }

  @ApiStatus.Internal
  protected ProjectRootManager() {
  }

  /**
   * Returns the file index for the project.
   *
   * @return the file index instance.
   */
  public abstract @NotNull ProjectFileIndex getFileIndex();

  /**
   * Creates new enumerator instance to process dependencies of all modules in the project. Only first level dependencies of
   * modules are processed so {@link OrderEnumerator#recursively()} option is ignored and {@link OrderEnumerator#withoutDepModules()} option is forced
   * @return new enumerator instance
   */
  public abstract @NotNull OrderEnumerator orderEntries();

  /**
   * Creates new enumerator instance to process dependencies of several modules in the project. Caching is not supported for this enumerator
   * @param modules modules to process
   * @return new enumerator instance
   */
  public abstract @NotNull OrderEnumerator orderEntries(@NotNull Collection<? extends Module> modules);

  /**
   * Unlike getContentRoots(), this includes the project base dir. Is this really necessary?
   * TODO: remove this method?
   */
  public abstract VirtualFile @NotNull [] getContentRootsFromAllModules();

  /**
   * Returns the list of content root URLs for all modules in the project.
   *
   * @return the list of content root URLs.
   */
  public abstract @NotNull List<String> getContentRootUrls();

    /**
    * Returns the list of content roots for all modules in the project.
    *
    * @return the array of content roots.
    */
  public abstract VirtualFile @NotNull [] getContentRoots();

  /**
   * Returns the list of source roots under the content roots for all modules in the project.
   *
   * @return the array of content source roots.
   */
  public abstract VirtualFile @NotNull [] getContentSourceRoots();

  /**
   * Returns the list of source roots from all modules which types belong to the specified set
   *
   * @param rootTypes types of source roots
   * @return list of source roots
   */
  public abstract @NotNull List<VirtualFile> getModuleSourceRoots(@NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes);

  /**
   * @return the instance of the JDK selected for the project or null
   * if the name of the selected JDK does not correspond to any existing JDK instance
   */
  public abstract @Nullable Sdk getProjectSdk();

  /**
   * @return the name of the SDK selected for the project
   */
  public abstract @Nullable String getProjectSdkName();

  /**
   * @return the SDK type name (@link {@link SdkTypeId#getName()} of the current Project SDK
   */
  public abstract @Nullable String getProjectSdkTypeName();

  /**
   * Sets the SDK to be used for the project.
   *
   * @param sdk the SDK instance.
   */
  @RequiresWriteLock
  public abstract void setProjectSdk(@Nullable Sdk sdk);

  /**
   * Sets the name of the JDK to be used for the project
   * @param sdkTypeName the {@link SdkTypeId#getName()} of the SDK type
   */
  @RequiresWriteLock
  public abstract void setProjectSdkName(@NotNull String name, @NotNull String sdkTypeName);

  @ApiStatus.Internal
  public abstract ModuleRootManager getModuleRootManager(@NotNull Module module);
}
