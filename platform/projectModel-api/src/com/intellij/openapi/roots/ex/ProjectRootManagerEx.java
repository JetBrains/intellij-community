// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ex;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

public abstract class ProjectRootManagerEx extends ProjectRootManager {
  public static ProjectRootManagerEx getInstanceEx(Project project) {
    return (ProjectRootManagerEx)getInstance(project);
  }

  public abstract void addProjectJdkListener(@NotNull ProjectJdkListener listener);

  public abstract void removeProjectJdkListener(@NotNull ProjectJdkListener listener);

  /**
   * Invokes runnable surrounded by beforeRootsChange()/rootsChanged() callbacks
   * <p>
   * With {@code !fileTypes && fireEvents} indexes always make a full rescan.
   * <p>
   * @deprecated Use {@link ProjectRootManagerEx#makeRootsChange(Runnable, RootsChangeRescanningInfo)} when {@code fireEvents == true},
   * else just {@code runnable.run()}
   * <p>
   * {@link RootsChangeRescanningInfo} allows to limit the scope of rescanning. It may be configured
   * with {@link com.intellij.util.indexing.BuildableRootsChangeRescanningInfo}
   */
  @Deprecated
  public abstract void makeRootsChange(@NotNull Runnable runnable, boolean fileTypes, boolean fireEvents);

  public abstract void makeRootsChange(@NotNull Runnable runnable, @NotNull RootsChangeRescanningInfo changes);

  public abstract @NotNull AutoCloseable withRootsChange(@NotNull RootsChangeRescanningInfo changes);

  public abstract @NotNull List<VirtualFile> markRootsForRefresh();

  public abstract void mergeRootsChangesDuring(@NotNull Runnable runnable);

  public abstract void clearScopesCachesForModules();

  /**
   * @see ProjectRootManagerEx#addProjectJdkListener(ProjectJdkListener)
   * @see ProjectRootManagerEx#removeProjectJdkListener(ProjectJdkListener)
   */
  @FunctionalInterface
  public interface ProjectJdkListener extends EventListener {
    void projectJdkChanged();
  }
}
