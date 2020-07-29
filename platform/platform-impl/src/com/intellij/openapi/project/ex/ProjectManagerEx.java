// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.ex;

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
import java.util.List;

public abstract class ProjectManagerEx extends ProjectManager {
  public static ProjectManagerEx getInstanceEx() {
    return (ProjectManagerEx)ApplicationManager.getApplication().getService(ProjectManager.class);
  }

  public static @Nullable ProjectManagerEx getInstanceExIfCreated() {
    return (ProjectManagerEx)ProjectManager.getInstanceIfCreated();
  }

  /**
   * @deprecated Use {@link #newProject(Path, OpenProjectTask)}
   */
  @Deprecated
  public abstract @Nullable Project newProject(@Nullable String projectName, @NotNull String filePath, boolean useDefaultProjectSettings, boolean isDummy);

  /**
   * @deprecated Pass {@code projectName} using {@link OpenProjectTask#projectName}.
   */
  @Deprecated
  public final @Nullable Project newProject(@NotNull Path file, @Nullable String projectName, @NotNull OpenProjectTask options) {
    return newProject(file, projectName == null ? options : options.withProjectName(projectName));
  }

  public abstract @Nullable Project newProject(@NotNull Path file, @NotNull OpenProjectTask options);

  /**
   * @deprecated Use {@link #loadProject(Path)}
   */
  @Deprecated
  public final @NotNull Project loadProject(@NotNull String filePath) {
    return loadProject(Paths.get(filePath).toAbsolutePath());
  }

  public abstract @Nullable Project openProject(@NotNull Path projectStoreBaseDir, @NotNull OpenProjectTask options);

  public abstract @NotNull Project loadProject(@NotNull Path path);

  public abstract boolean openProject(@NotNull Project project);

  @TestOnly
  public abstract boolean isDefaultProjectInitialized();

  public abstract boolean isProjectOpened(@NotNull Project project);

  public abstract boolean canClose(@NotNull Project project);

  /**
   * The project and the app settings will be not saved.
   */
  public abstract boolean forceCloseProject(@NotNull Project project);

  // return true if successful
  public abstract boolean closeAndDisposeAllProjects(boolean checkCanClose);

  public abstract @Nullable Project findOpenProjectByHash(@Nullable String locationHash);

  @ApiStatus.Internal
  public abstract @NotNull List<String> getAllExcludedUrls();
}
