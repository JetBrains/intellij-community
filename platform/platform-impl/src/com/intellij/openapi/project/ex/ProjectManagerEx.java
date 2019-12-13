// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.ex;

import com.intellij.configurationStore.StoreReloadManager;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

public abstract class ProjectManagerEx extends ProjectManager {
  public static ProjectManagerEx getInstanceEx() {
    return (ProjectManagerEx)ApplicationManager.getApplication().getService(ProjectManager.class);
  }

  @Nullable
  public static ProjectManagerEx getInstanceExIfCreated() {
    return (ProjectManagerEx)ProjectManager.getInstanceIfCreated();
  }

  /**
   * @param filePath path to .ipr file or directory where .idea directory is located
   */
  @Nullable
  public abstract Project newProject(@Nullable String projectName, @NotNull String filePath, boolean useDefaultProjectSettings, boolean isDummy);

  @TestOnly
  public final Project newProjectForTest(@NotNull Path file) {
    OpenProjectTask options = new OpenProjectTask();
    options.useDefaultProjectAsTemplate = false;
    options.isNewProject = true;
    return newProject(file, null, options);
  }

  @Nullable
  public abstract Project newProject(@NotNull Path file, @Nullable String projectName, @NotNull OpenProjectTask options);

  /**
   * @deprecated Use {@link #loadProject(Path)}
   */
  @NotNull
  @Deprecated
  public final Project loadProject(@NotNull String filePath) {
    return loadProject(Paths.get(filePath).toAbsolutePath(), null);
  }

  @NotNull
  public final Project loadProject(@NotNull Path path) {
    return loadProject(path, null);
  }

  @NotNull
  public abstract Project loadProject(@NotNull Path file, @Nullable String projectName);

  public abstract boolean openProject(@NotNull Project project);

  @TestOnly
  public abstract boolean isDefaultProjectInitialized();

  public abstract boolean isProjectOpened(Project project);

  public abstract boolean canClose(@NotNull Project project);

  /**
   * @deprecated Use {@link StoreReloadManager#blockReloadingProjectOnExternalChanges()}
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Deprecated
  public final void blockReloadingProjectOnExternalChanges() {
    StoreReloadManager.getInstance().blockReloadingProjectOnExternalChanges();
  }

  /**
   * @deprecated Use {@link StoreReloadManager#blockReloadingProjectOnExternalChanges()}
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Deprecated
  public final void unblockReloadingProjectOnExternalChanges() {
    StoreReloadManager.getInstance().unblockReloadingProjectOnExternalChanges();
  }

  @TestOnly
  public abstract void openTestProject(@NotNull Project project);

  /**
   * Without save and "check can close".
   * Returns remaining open test projects.
   */
  @TestOnly
  @NotNull
  public abstract Collection<Project> closeTestProject(@NotNull Project project);

  /**
   * The project and the app settings will be not saved.
   */
  public abstract boolean forceCloseProject(@NotNull Project project, boolean dispose);

  // return true if successful
  public abstract boolean closeAndDisposeAllProjects(boolean checkCanClose);

  /**
   * Save, close and dispose project. Please note that only the project will be saved, but not the application.
   * @return true on success
   */
  public abstract boolean closeAndDispose(@NotNull Project project);

  @Nullable
  @Override
  public Project createProject(@Nullable String name, @NotNull String path) {
    return newProject(name, path, true, false);
  }

  @Nullable
  public abstract Project findOpenProjectByHash(@Nullable String locationHash);

  @NotNull
  @ApiStatus.Internal
  public abstract String[] getAllExcludedUrls();
}
