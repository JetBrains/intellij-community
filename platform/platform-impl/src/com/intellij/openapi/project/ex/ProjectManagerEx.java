// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.Collection;

public abstract class ProjectManagerEx extends ProjectManager {
  public static ProjectManagerEx getInstanceEx() {
    return (ProjectManagerEx)ApplicationManager.getApplication().getComponent(ProjectManager.class);
  }

  /**
   * @param filePath path to .ipr file or directory where .idea directory is located
   */
  @Nullable
  public abstract Project newProject(@Nullable String projectName, @NotNull String filePath, boolean useDefaultProjectSettings, boolean isDummy);

  @TestOnly
  @NotNull
  public final Project newProject(@Nullable String projectName, @NotNull String filePath) {
    return ObjectUtils.assertNotNull(newProject(projectName, filePath, false, false));
  }

  @Nullable
  public abstract Project loadProject(@NotNull String filePath) throws IOException;

  @Nullable
  public abstract Project loadProject(@NotNull String filePath, @Nullable String projectName) throws IOException;

  public abstract boolean openProject(@NotNull Project project);

  @TestOnly
  public abstract boolean isDefaultProjectInitialized();

  public abstract boolean isProjectOpened(Project project);

  public abstract boolean canClose(@NotNull Project project);

  public abstract void saveChangedProjectFile(@NotNull VirtualFile file, @NotNull Project project);

  public abstract void blockReloadingProjectOnExternalChanges();
  public abstract void unblockReloadingProjectOnExternalChanges();

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
  @TestOnly
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

  @Nullable
  public abstract Project convertAndLoadProject(@NotNull String filePath) throws IOException;

  /**
   * Internal use only. Force reload changed project files. Must be called before save otherwise saving maybe not performed (because storage saving is disabled).
   */
  public void flushChangedProjectFileAlarm() {
  }
}
