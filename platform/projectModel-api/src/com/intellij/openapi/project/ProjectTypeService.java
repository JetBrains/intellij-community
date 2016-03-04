/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.project;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */

@State(name = "ProjectType")
public class ProjectTypeService implements PersistentStateComponent<ProjectType> {

  private ProjectType myProjectType;

  @Nullable
  public static ProjectType getProjectType(@Nullable Project project) {
    ProjectType projectType;
    if (project != null) {
      projectType = getInstance(project).myProjectType;
      if (projectType != null) return projectType;
    }
    return DefaultProjectTypeEP.getDefaultProjectType();
  }

  public static void setProjectType(@NotNull Project project, @Nullable ProjectType projectType) {
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
  public void loadState(ProjectType state) {
    myProjectType = state;
  }
}
