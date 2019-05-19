// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author Dmitry Avdeev
 */
@State(name = "ProjectType")
public class ProjectTypeService implements PersistentStateComponent<ProjectType> {
  private ProjectType myProjectType;

  @Nullable
  public static ProjectType getProjectType(@Nullable Project project) {
    if (project != null) {
      ProjectType projectType = getInstance(project).myProjectType;
      if (projectType != null) return projectType;
    }
    return DefaultProjectTypeEP.getDefaultProjectType();
  }

  public static void setProjectType(@NotNull Project project, @NotNull ProjectType projectType) {
    getInstance(project).loadState(projectType);
  }

  private static ProjectTypeService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectTypeService.class);
  }

  @Nullable
  @Override
  public ProjectType getState() {
    return myProjectType;
  }

  @Override
  public void loadState(@NotNull ProjectType state) {
    myProjectType = state;
  }

  @TestOnly
  public static void clearFieldsForLightProjectInTests(@NotNull Project project) {
    getInstance(project).myProjectType = null;
  }
}
